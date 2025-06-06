/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.cloud.kubernetes.cluster;

import com.cloud.api.query.dao.TemplateJoinDao;
import com.cloud.api.query.vo.TemplateJoinVO;
import com.cloud.dc.DataCenter;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.kubernetes.cluster.actionworkers.KubernetesClusterActionWorker;
import com.cloud.kubernetes.cluster.dao.KubernetesClusterDao;
import com.cloud.kubernetes.cluster.dao.KubernetesClusterVmMapDao;
import com.cloud.network.Network;
import com.cloud.network.dao.FirewallRulesDao;
import com.cloud.network.rules.FirewallRule;
import com.cloud.network.rules.FirewallRuleVO;
import com.cloud.network.vpc.NetworkACL;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.User;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.command.user.kubernetes.cluster.AddVirtualMachinesToKubernetesClusterCmd;
import org.apache.cloudstack.api.command.user.kubernetes.cluster.RemoveVirtualMachinesFromKubernetesClusterCmd;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesClusterManagerImplTest {

    @Mock
    FirewallRulesDao firewallRulesDao;

    @Mock
    VMTemplateDao templateDao;

    @Mock
    TemplateJoinDao templateJoinDao;

    @Mock
    KubernetesClusterDao kubernetesClusterDao;

    @Mock
    KubernetesClusterVmMapDao kubernetesClusterVmMapDao;

    @Mock
    VMInstanceDao vmInstanceDao;

    @Mock
    private AccountManager accountManager;

    @Spy
    @InjectMocks
    KubernetesClusterManagerImpl kubernetesClusterManager;

    @Test
    public void testValidateVpcTierAllocated() {
        Network network = Mockito.mock(Network.class);
        Mockito.when(network.getState()).thenReturn(Network.State.Allocated);
        kubernetesClusterManager.validateVpcTier(network);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testValidateVpcTierDefaultDenyRule() {
        Network network = Mockito.mock(Network.class);
        Mockito.when(network.getState()).thenReturn(Network.State.Implemented);
        Mockito.when(network.getNetworkACLId()).thenReturn(NetworkACL.DEFAULT_DENY);
        kubernetesClusterManager.validateVpcTier(network);
    }

    @Test
    public void testValidateVpcTierValid() {
        Network network = Mockito.mock(Network.class);
        Mockito.when(network.getState()).thenReturn(Network.State.Implemented);
        Mockito.when(network.getNetworkACLId()).thenReturn(NetworkACL.DEFAULT_ALLOW);
        kubernetesClusterManager.validateVpcTier(network);
    }

    @Test
    public void validateIsolatedNetworkIpRulesNoRules() {
        long ipId = 1L;
        FirewallRule.Purpose purpose = FirewallRule.Purpose.Firewall;
        Network network = Mockito.mock(Network.class);
        Mockito.when(firewallRulesDao.listByIpPurposeProtocolAndNotRevoked(ipId, purpose, NetUtils.TCP_PROTO)).thenReturn(new ArrayList<>());
        kubernetesClusterManager.validateIsolatedNetworkIpRules(ipId, FirewallRule.Purpose.Firewall, network, 3);
    }

    private FirewallRuleVO createRule(int startPort, int endPort) {
        FirewallRuleVO rule = new FirewallRuleVO(null, null, startPort, endPort, "tcp", 1, 1, 1, FirewallRule.Purpose.Firewall, List.of("0.0.0.0/0"), null, null, null, FirewallRule.TrafficType.Ingress);
        return rule;
    }

    @Test
    public void validateIsolatedNetworkIpRulesNoConflictingRules() {
        long ipId = 1L;
        FirewallRule.Purpose purpose = FirewallRule.Purpose.Firewall;
        Network network = Mockito.mock(Network.class);
        Mockito.when(firewallRulesDao.listByIpPurposeProtocolAndNotRevoked(ipId, purpose, NetUtils.TCP_PROTO)).thenReturn(List.of(createRule(80, 80), createRule(443, 443)));
        kubernetesClusterManager.validateIsolatedNetworkIpRules(ipId, FirewallRule.Purpose.Firewall, network, 3);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateIsolatedNetworkIpRulesApiConflictingRules() {
        long ipId = 1L;
        FirewallRule.Purpose purpose = FirewallRule.Purpose.Firewall;
        Network network = Mockito.mock(Network.class);
        Mockito.when(firewallRulesDao.listByIpPurposeProtocolAndNotRevoked(ipId, purpose, NetUtils.TCP_PROTO)).thenReturn(List.of(createRule(6440, 6445), createRule(443, 443)));
        kubernetesClusterManager.validateIsolatedNetworkIpRules(ipId, FirewallRule.Purpose.Firewall, network, 3);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateIsolatedNetworkIpRulesSshConflictingRules() {
        long ipId = 1L;
        FirewallRule.Purpose purpose = FirewallRule.Purpose.Firewall;
        Network network = Mockito.mock(Network.class);
        Mockito.when(firewallRulesDao.listByIpPurposeProtocolAndNotRevoked(ipId, purpose, NetUtils.TCP_PROTO)).thenReturn(List.of(createRule(2200, KubernetesClusterActionWorker.CLUSTER_NODES_DEFAULT_START_SSH_PORT), createRule(443, 443)));
        kubernetesClusterManager.validateIsolatedNetworkIpRules(ipId, FirewallRule.Purpose.Firewall, network, 3);
    }

    @Test
    public void validateIsolatedNetworkIpRulesNearConflictingRules() {
        long ipId = 1L;
        FirewallRule.Purpose purpose = FirewallRule.Purpose.Firewall;
        Network network = Mockito.mock(Network.class);
        Mockito.when(firewallRulesDao.listByIpPurposeProtocolAndNotRevoked(ipId, purpose, NetUtils.TCP_PROTO)).thenReturn(List.of(createRule(2220, 2221), createRule(2225, 2227), createRule(6440, 6442), createRule(6444, 6446)));
        kubernetesClusterManager.validateIsolatedNetworkIpRules(ipId, FirewallRule.Purpose.Firewall, network, 3);
    }

    @Test
    public void testValidateKubernetesClusterScaleSizeNullNewSizeNoError() {
        kubernetesClusterManager.validateKubernetesClusterScaleSize(Mockito.mock(KubernetesClusterVO.class), null, 100, Mockito.mock(DataCenter.class));
    }

    @Test
    public void testValidateKubernetesClusterScaleSizeSameNewSizeNoError() {
        Long size = 2L;
        KubernetesClusterVO clusterVO = Mockito.mock(KubernetesClusterVO.class);
        Mockito.when(clusterVO.getNodeCount()).thenReturn(size);
        kubernetesClusterManager.validateKubernetesClusterScaleSize(clusterVO, size, 100, Mockito.mock(DataCenter.class));
    }

    @Test(expected = PermissionDeniedException.class)
    public void testValidateKubernetesClusterScaleSizeStoppedCluster() {
        Long size = 2L;
        KubernetesClusterVO clusterVO = Mockito.mock(KubernetesClusterVO.class);
        Mockito.when(clusterVO.getNodeCount()).thenReturn(size);
        Mockito.when(clusterVO.getState()).thenReturn(KubernetesCluster.State.Stopped);
        kubernetesClusterManager.validateKubernetesClusterScaleSize(clusterVO, 3L, 100, Mockito.mock(DataCenter.class));
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testValidateKubernetesClusterScaleSizeZeroNewSize() {
        Long size = 2L;
        KubernetesClusterVO clusterVO = Mockito.mock(KubernetesClusterVO.class);
        Mockito.when(clusterVO.getState()).thenReturn(KubernetesCluster.State.Running);
        Mockito.when(clusterVO.getNodeCount()).thenReturn(size);
        kubernetesClusterManager.validateKubernetesClusterScaleSize(clusterVO, 0L, 100, Mockito.mock(DataCenter.class));
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testValidateKubernetesClusterScaleSizeOverMaxSize() {
        KubernetesClusterVO clusterVO = Mockito.mock(KubernetesClusterVO.class);
        Mockito.when(clusterVO.getState()).thenReturn(KubernetesCluster.State.Running);
        Mockito.when(clusterVO.getControlNodeCount()).thenReturn(1L);
        kubernetesClusterManager.validateKubernetesClusterScaleSize(clusterVO, 4L, 4, Mockito.mock(DataCenter.class));
    }

    @Test
    public void testValidateKubernetesClusterScaleSizeDownscaleNoError() {
        KubernetesClusterVO clusterVO = Mockito.mock(KubernetesClusterVO.class);
        Mockito.when(clusterVO.getState()).thenReturn(KubernetesCluster.State.Running);
        Mockito.when(clusterVO.getControlNodeCount()).thenReturn(1L);
        Mockito.when(clusterVO.getNodeCount()).thenReturn(4L);
        kubernetesClusterManager.validateKubernetesClusterScaleSize(clusterVO, 2L, 10, Mockito.mock(DataCenter.class));
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testValidateKubernetesClusterScaleSizeUpscaleDeletedTemplate() {
        KubernetesClusterVO clusterVO = Mockito.mock(KubernetesClusterVO.class);
        Mockito.when(clusterVO.getState()).thenReturn(KubernetesCluster.State.Running);
        Mockito.when(clusterVO.getControlNodeCount()).thenReturn(1L);
        Mockito.when(clusterVO.getNodeCount()).thenReturn(2L);
        Mockito.when(templateDao.findById(Mockito.anyLong())).thenReturn(null);
        kubernetesClusterManager.validateKubernetesClusterScaleSize(clusterVO, 4L, 10, Mockito.mock(DataCenter.class));
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testValidateKubernetesClusterScaleSizeUpscaleNotInZoneTemplate() {
        KubernetesClusterVO clusterVO = Mockito.mock(KubernetesClusterVO.class);
        Mockito.when(clusterVO.getState()).thenReturn(KubernetesCluster.State.Running);
        Mockito.when(clusterVO.getControlNodeCount()).thenReturn(1L);
        Mockito.when(clusterVO.getNodeCount()).thenReturn(2L);
        Mockito.when(templateDao.findById(Mockito.anyLong())).thenReturn(Mockito.mock(VMTemplateVO.class));
        Mockito.when(templateJoinDao.newTemplateView(Mockito.any(VMTemplateVO.class), Mockito.anyLong(), Mockito.anyBoolean())).thenReturn(null);
        kubernetesClusterManager.validateKubernetesClusterScaleSize(clusterVO, 4L, 10, Mockito.mock(DataCenter.class));
    }

    @Test
    public void testValidateKubernetesClusterScaleSizeUpscaleNoError() {
        KubernetesClusterVO clusterVO = Mockito.mock(KubernetesClusterVO.class);
        Mockito.when(clusterVO.getState()).thenReturn(KubernetesCluster.State.Running);
        Mockito.when(clusterVO.getControlNodeCount()).thenReturn(1L);
        Mockito.when(clusterVO.getNodeCount()).thenReturn(2L);
        Mockito.when(templateDao.findById(Mockito.anyLong())).thenReturn(Mockito.mock(VMTemplateVO.class));
        Mockito.when(templateJoinDao.newTemplateView(Mockito.any(VMTemplateVO.class), Mockito.anyLong(), Mockito.anyBoolean())).thenReturn(List.of(Mockito.mock(TemplateJoinVO.class)));
        kubernetesClusterManager.validateKubernetesClusterScaleSize(clusterVO, 4L, 10, Mockito.mock(DataCenter.class));

    }

    @Before
    public void setUp() throws Exception {
        CallContext.register(Mockito.mock(User.class), Mockito.mock(Account.class));
        overrideDefaultConfigValue(KubernetesClusterService.KubernetesServiceEnabled, "_defaultValue", "true");
        Mockito.doNothing().when(accountManager).checkAccess(
                Mockito.any(Account.class), Mockito.any(), Mockito.anyBoolean(), Mockito.any());
    }

    @After
    public void tearDown() throws Exception {
        CallContext.unregister();
    }

    private void overrideDefaultConfigValue(final ConfigKey configKey, final String name, final Object o) throws IllegalAccessException, NoSuchFieldException {
        Field f = ConfigKey.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(configKey, o);
    }

    @Test
    public void addVmsToCluster() {
        KubernetesClusterVO cluster = Mockito.mock(KubernetesClusterVO.class);
        VMInstanceVO vm = Mockito.mock(VMInstanceVO.class);
        AddVirtualMachinesToKubernetesClusterCmd cmd = Mockito.mock(AddVirtualMachinesToKubernetesClusterCmd.class);
        List<Long> vmIds = Arrays.asList(1L, 2L, 3L);

        Mockito.when(cmd.getId()).thenReturn(1L);
        Mockito.when(cmd.getVmIds()).thenReturn(vmIds);
        Mockito.when(cmd.getActualCommandName()).thenReturn(BaseCmd.getCommandNameByClass(RemoveVirtualMachinesFromKubernetesClusterCmd.class));
        Mockito.when(cluster.getClusterType()).thenReturn(KubernetesCluster.ClusterType.ExternalManaged);
        Mockito.when(vmInstanceDao.findById(Mockito.anyLong())).thenReturn(vm);
        Mockito.when(kubernetesClusterDao.findById(Mockito.anyLong())).thenReturn(cluster);
        Mockito.when(kubernetesClusterVmMapDao.listByClusterIdAndVmIdsIn(1L, vmIds)).thenReturn(Collections.emptyList());
        Assert.assertTrue(kubernetesClusterManager.addVmsToCluster(cmd));
    }

    @Test
    public void removeVmsFromCluster() {
        KubernetesClusterVO cluster = Mockito.mock(KubernetesClusterVO.class);
        RemoveVirtualMachinesFromKubernetesClusterCmd cmd = Mockito.mock(RemoveVirtualMachinesFromKubernetesClusterCmd.class);
        List<Long> vmIds = Arrays.asList(1L, 2L, 3L);

        Mockito.when(cmd.getId()).thenReturn(1L);
        Mockito.when(cmd.getVmIds()).thenReturn(vmIds);
        Mockito.when(cmd.getActualCommandName()).thenReturn(BaseCmd.getCommandNameByClass(RemoveVirtualMachinesFromKubernetesClusterCmd.class));
        Mockito.when(cluster.getClusterType()).thenReturn(KubernetesCluster.ClusterType.ExternalManaged);
        Mockito.when(kubernetesClusterDao.findById(Mockito.anyLong())).thenReturn(cluster);
        Assert.assertTrue(kubernetesClusterManager.removeVmsFromCluster(cmd).size() > 0);
    }
}
