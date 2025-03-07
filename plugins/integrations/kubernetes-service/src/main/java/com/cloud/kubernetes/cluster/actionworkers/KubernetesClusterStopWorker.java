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

package com.cloud.kubernetes.cluster.actionworkers;

import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.cloudstack.api.ApiCommandResourceType;
import org.apache.cloudstack.context.CallContext;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.kubernetes.cluster.KubernetesCluster;
import com.cloud.kubernetes.cluster.KubernetesClusterManagerImpl;
import com.cloud.uservm.UserVm;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VirtualMachine;

public class KubernetesClusterStopWorker extends KubernetesClusterActionWorker {
    public KubernetesClusterStopWorker(final KubernetesCluster kubernetesCluster, final KubernetesClusterManagerImpl clusterManager) {
        super(kubernetesCluster, clusterManager);
    }

    public boolean stop() throws CloudRuntimeException {
        init();
        if (logger.isInfoEnabled()) {
            logger.info("Stopping Kubernetes cluster: {}", kubernetesCluster);
        }
        stateTransitTo(kubernetesCluster.getId(), KubernetesCluster.Event.StopRequested);
        List<UserVm> clusterVMs = getKubernetesClusterVMs();
        for (UserVm vm : clusterVMs) {
            if (vm == null) {
                logTransitStateAndThrow(Level.ERROR, String.format("Failed to find all VMs in Kubernetes cluster : %s", kubernetesCluster.getName()), kubernetesCluster.getId(), KubernetesCluster.Event.OperationFailed);
            }
            CallContext vmContext = CallContext.register(CallContext.current(), ApiCommandResourceType.VirtualMachine);
            vmContext.setEventResourceId(vm.getId());
            try {
                userVmService.stopVirtualMachine(vm.getId(), false);
            } catch (ConcurrentOperationException ex) {
                logger.warn("Failed to stop VM: {} in Kubernetes cluster: {}", vm, kubernetesCluster, ex);
            } finally {
                CallContext.unregister();
            }
        }
        for (final UserVm userVm : clusterVMs) {
            UserVm vm = userVmDao.findById(userVm.getId());
            if (vm == null || !vm.getState().equals(VirtualMachine.State.Stopped)) {
                logTransitStateAndThrow(Level.ERROR, String.format("Failed to stop all VMs in Kubernetes cluster : %s",
                    kubernetesCluster.getName()), kubernetesCluster.getId(), KubernetesCluster.Event.OperationFailed);
            }
        }
        stateTransitTo(kubernetesCluster.getId(), KubernetesCluster.Event.OperationSucceeded);
        return true;
    }
}
