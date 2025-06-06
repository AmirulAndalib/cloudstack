//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.cloud.hypervisor.xenserver.resource.wrapper.xenbase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.cloud.agent.resource.virtualnetwork.VRScripts;
import com.cloud.agent.resource.virtualnetwork.VirtualRoutingResource;
import org.apache.commons.lang3.StringUtils;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.OvsSetTagAndFlowAnswer;
import com.cloud.agent.api.OvsSetTagAndFlowCommand;
import com.cloud.agent.api.StartAnswer;
import com.cloud.agent.api.StartCommand;
import com.cloud.agent.api.to.DiskTO;
import com.cloud.agent.api.to.GPUDeviceTO;
import com.cloud.agent.api.to.NicTO;
import com.cloud.agent.api.to.VirtualMachineTO;
import com.cloud.hypervisor.xenserver.resource.CitrixResourceBase;
import com.cloud.network.Networks;
import com.cloud.network.Networks.BroadcastDomainType;
import com.cloud.network.Networks.IsolationType;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.storage.Storage;
import com.cloud.storage.Volume;
import com.cloud.vm.VirtualMachine;
import com.xensource.xenapi.Connection;
import com.xensource.xenapi.Host;
import com.xensource.xenapi.Types.VmPowerState;
import com.xensource.xenapi.VDI;
import com.xensource.xenapi.VM;

@ResourceWrapper(handles =  StartCommand.class)
public final class CitrixStartCommandWrapper extends CommandWrapper<StartCommand, Answer, CitrixResourceBase> {


    @Override
    public Answer execute(final StartCommand command, final CitrixResourceBase citrixResourceBase) {
        final Connection conn = citrixResourceBase.getConnection();
        final VirtualMachineTO vmSpec = command.getVirtualMachine();
        final String vmName = vmSpec.getName();
        VmPowerState state = VmPowerState.HALTED;
        VM vm = null;
        // if a VDI is created, record its UUID and its type (ex. VHD) to send back to the CS MS
        final Map<String, Map<String, String>> iqnToData = new HashMap<>();

        try {
            final Set<VM> vms = VM.getByNameLabel(conn, vmName);
            if (vms != null) {
                for (final VM v : vms) {
                    final VM.Record vRec = v.getRecord(conn);
                    if (vRec.powerState == VmPowerState.HALTED) {
                        citrixResourceBase.destroyVm(v, conn, true);
                    } else if (vRec.powerState == VmPowerState.RUNNING) {
                        final String host = vRec.residentOn.getUuid(conn);
                        final String msg = "VM " + vmName + " is runing on host " + host;
                        logger.debug(msg);
                        return new StartAnswer(command, msg, host);
                    } else {
                        final String msg = "There is already a VM having the same name " + vmName + " vm record " + vRec.toString();
                        logger.warn(msg);
                        return new StartAnswer(command, msg);
                    }
                }
            }
            logger.debug("1. The VM " + vmName + " is in Starting state.");

            final Host host = Host.getByUuid(conn, citrixResourceBase.getHost().getUuid());
            vm = citrixResourceBase.createVmFromTemplate(conn, vmSpec, host);
            final GPUDeviceTO gpuDevice = vmSpec.getGpuDevice();
            if (gpuDevice != null) {
                logger.debug("Creating VGPU for of VGPU type: " + gpuDevice.getVgpuType() + " in GPU group " + gpuDevice.getGpuGroup() + " for VM " + vmName);
                citrixResourceBase.createVGPU(conn, command, vm, gpuDevice);
            }

            Host.Record record = host.getRecord(conn);
            String xenBrand = record.softwareVersion.get("product_brand");
            String xenVersion = record.softwareVersion.get("product_version");
            boolean requiresGuestTools = true;
            if (xenBrand.equals("XenServer") && isVersionGreaterThanOrEqual(xenVersion, "8.2.0")) {
                requiresGuestTools = false;
            }

            if (vmSpec.getType() != VirtualMachine.Type.User && requiresGuestTools) {
                citrixResourceBase.createPatchVbd(conn, vmName, vm);
            }

            prepareDisks(vmSpec, citrixResourceBase, conn, iqnToData, vmName, vm);

            for (final NicTO nic : vmSpec.getNics()) {
                citrixResourceBase.createVif(conn, vmName, vm, vmSpec, nic);
            }

            citrixResourceBase.startVM(conn, host, vm, vmName);

            if (citrixResourceBase.isOvs()) {
                // TODO(Salvatore-orlando): This code should go
                for (final NicTO nic : vmSpec.getNics()) {
                    if (nic.getBroadcastType() == Networks.BroadcastDomainType.Vswitch) {
                        final HashMap<String, String> args = citrixResourceBase.parseDefaultOvsRuleCommand(BroadcastDomainType.getValue(nic.getBroadcastUri()));
                        final OvsSetTagAndFlowCommand flowCmd = new OvsSetTagAndFlowCommand(args.get("vmName"), args.get("tag"), args.get("vlans"), args.get("seqno"),
                                Long.parseLong(args.get("vmId")));

                        final CitrixRequestWrapper citrixRequestWrapper = CitrixRequestWrapper.getInstance();

                        final OvsSetTagAndFlowAnswer r = (OvsSetTagAndFlowAnswer) citrixRequestWrapper.execute(flowCmd, citrixResourceBase);

                        if (!r.getResult()) {
                            logger.warn("Failed to set flow for VM " + r.getVmId());
                        } else {
                            logger.info("Success to set flow for VM " + r.getVmId());
                        }
                    }
                }
            }

            if (citrixResourceBase.canBridgeFirewall()) {
                String result = null;
                if (vmSpec.getType() != VirtualMachine.Type.User) {
                    final NicTO[] nics = vmSpec.getNics();
                    boolean secGrpEnabled = false;
                    for (final NicTO nic : nics) {
                        if (nic.isSecurityGroupEnabled() || nic.getIsolationUri() != null && nic.getIsolationUri().getScheme().equalsIgnoreCase(IsolationType.Ec2.toString())) {
                            secGrpEnabled = true;
                            break;
                        }
                    }
                    if (secGrpEnabled) {
                        result = citrixResourceBase.callHostPlugin(conn, "vmops", "default_network_rules_systemvm", "vmName", vmName);
                        if (result == null || result.isEmpty() || !Boolean.parseBoolean(result)) {
                            logger.warn("Failed to program default network rules for " + vmName);
                        } else {
                            logger.info("Programmed default network rules for " + vmName);
                        }
                    }

                } else {
                    // For user vm, program the rules for each nic if the
                    // isolation uri scheme is ec2
                    final NicTO[] nics = vmSpec.getNics();
                    for (final NicTO nic : nics) {
                        if (nic.isSecurityGroupEnabled() || nic.getIsolationUri() != null && nic.getIsolationUri().getScheme().equalsIgnoreCase(IsolationType.Ec2.toString())) {
                            final List<String> nicSecIps = nic.getNicSecIps();
                            String secIpsStr;
                            final StringBuilder sb = new StringBuilder();
                            if (nicSecIps != null) {
                                for (final String ip : nicSecIps) {
                                    sb.append(ip).append(";");
                                }
                                secIpsStr = sb.toString();
                            } else {
                                secIpsStr = "0;";
                            }
                            result = citrixResourceBase.callHostPlugin(conn, "vmops", "default_network_rules", "vmName", vmName, "vmIP", nic.getIp(), "vmMAC", nic.getMac(),
                                    "vmID", Long.toString(vmSpec.getId()), "secIps", secIpsStr);

                            if (result == null || result.isEmpty() || !Boolean.parseBoolean(result)) {
                                logger.warn("Failed to program default network rules for " + vmName + " on nic with ip:" + nic.getIp() + " mac:" + nic.getMac());
                            } else {
                                logger.info("Programmed default network rules for " + vmName + " on nic with ip:" + nic.getIp() + " mac:" + nic.getMac());
                            }
                        }
                    }
                }
            }

            state = VmPowerState.RUNNING;

            if (vmSpec.getType() != VirtualMachine.Type.User) {
                String controlIp = null;
                for (final NicTO nic : vmSpec.getNics()) {
                    if (nic.getType() == Networks.TrafficType.Control) {
                        controlIp = nic.getIp();
                        break;
                    }
                }

                String result2 = citrixResourceBase.connect(conn, vmName, controlIp, 1000);
                if (StringUtils.isEmpty(result2)) {
                    logger.info(String.format("Connected to SystemVM: %s", vmName));
                }

                try {
                    citrixResourceBase.copyPatchFilesToVR(controlIp, VRScripts.CONFIG_CACHE_LOCATION);
                    VirtualRoutingResource vrResource = citrixResourceBase.getVirtualRoutingResource();
                    if (!vrResource.isSystemVMSetup(vmName, controlIp)) {
                        String errMsg = "Failed to patch systemVM";
                        logger.error(errMsg);
                        return new StartAnswer(command, errMsg);
                    }
                } catch (Exception e) {
                    String errMsg = "Failed to scp files to system VM. Patching of systemVM failed";
                    logger.error(errMsg, e);
                    return new StartAnswer(command, String.format("%s due to: %s", errMsg, e.getMessage()));
                }
            }

            final StartAnswer startAnswer = new StartAnswer(command);

            startAnswer.setIqnToData(iqnToData);

            return startAnswer;
        } catch (final Exception e) {
            logger.warn("Catch Exception: " + e.getClass().toString() + " due to " + e.toString(), e);
            final String msg = citrixResourceBase.handleVmStartFailure(conn, vmName, vm, "", e);

            final StartAnswer startAnswer = new StartAnswer(command, msg);

            startAnswer.setIqnToData(iqnToData);

            return startAnswer;
        } finally {
            if (state != VmPowerState.HALTED) {
                logger.debug("2. The VM " + vmName + " is in " + state + " state.");
            } else {
                logger.debug("The VM is in stopped state, detected problem during startup : " + vmName);
            }
        }
    }

    private void prepareDisks(VirtualMachineTO vmSpec, CitrixResourceBase citrixResourceBase, Connection conn,
                              Map<String, Map<String, String>> iqnToData, String vmName, VM vm) throws Exception {
        // put cdrom at the first place in the list
        List<DiskTO> disks = new ArrayList<DiskTO>(vmSpec.getDisks().length);
        int index = 0;
        for (final DiskTO disk : vmSpec.getDisks()) {
            if (Volume.Type.ISO.equals(disk.getType()) && Objects.nonNull(disk.getPath())) {
                disks.add(0, disk);
            } else {
                disks.add(index, disk);
            }
            index++;
        }
        int isoCount = 0;
        for (DiskTO disk : disks) {
            final VDI newVdi = citrixResourceBase.prepareManagedDisk(conn, disk, vmSpec.getId(), vmSpec.getName());
            if (newVdi != null) {
                final Map<String, String> data = new HashMap<>();
                final String path = newVdi.getUuid(conn);
                data.put(StartAnswer.PATH, path);
                data.put(StartAnswer.IMAGE_FORMAT, Storage.ImageFormat.VHD.toString());
                iqnToData.put(disk.getDetails().get(DiskTO.IQN), data);
            }
            citrixResourceBase.createVbd(conn, disk, vmName, vm, vmSpec.getBootloader(), newVdi, isoCount);

            if (disk.getType() == Volume.Type.ISO) {
                isoCount++;
            }
        }
    }

    public static boolean isVersionGreaterThanOrEqual(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

            if (num1 > num2) return true;
            if (num1 < num2) return false;
        }
        return true; // versions are equal
    }
}
