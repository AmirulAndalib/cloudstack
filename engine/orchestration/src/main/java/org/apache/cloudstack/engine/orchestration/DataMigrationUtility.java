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

package org.apache.cloudstack.engine.orchestration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.cloudstack.engine.subsystem.api.storage.DataObject;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine.State;
import org.apache.cloudstack.engine.subsystem.api.storage.SnapshotDataFactory;
import org.apache.cloudstack.engine.subsystem.api.storage.SnapshotInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.TemplateDataFactory;
import org.apache.cloudstack.engine.subsystem.api.storage.TemplateInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeDataFactory;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeInfo;
import org.apache.cloudstack.storage.ImageStoreService;
import org.apache.cloudstack.storage.datastore.db.SnapshotDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.SnapshotDataStoreVO;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreVO;
import org.apache.cloudstack.storage.datastore.db.VolumeDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.VolumeDataStoreVO;

import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.storage.DataStoreRole;
import com.cloud.storage.SnapshotVO;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.dao.SnapshotDao;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.utils.Pair;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.SecondaryStorageVmVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.SecondaryStorageVmDao;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class DataMigrationUtility {
    protected Logger logger = LogManager.getLogger(getClass());

    @Inject
    SecondaryStorageVmDao secStorageVmDao;
    @Inject
    TemplateDataStoreDao templateDataStoreDao;
    @Inject
    SnapshotDataStoreDao snapshotDataStoreDao;
    @Inject
    VolumeDataStoreDao volumeDataStoreDao;
    @Inject
    VMTemplateDao templateDao;
    @Inject
    VolumeDataFactory volumeFactory;
    @Inject
    TemplateDataFactory templateFactory;
    @Inject
    SnapshotDataFactory snapshotFactory;
    @Inject
    HostDao hostDao;
    @Inject
    SnapshotDao snapshotDao;
    /**
     *  This function verifies if the given image store contains data objects that are not in any of the following states:
     *  "Ready" "Allocated", "Destroying", "Destroyed", "Failed". If this is the case, and if the migration policy is complete,
     *  the migration is terminated.
     */
    public boolean filesReadyToMigrate(Long srcDataStoreId, List<TemplateDataStoreVO> templates, List<SnapshotDataStoreVO> snapshots, List<VolumeDataStoreVO> volumes) {
        State[] validStates = {State.Ready, State.Allocated, State.Destroying, State.Destroyed, State.Failed};
        boolean isReady = true;
        for (TemplateDataStoreVO template : templates) {
            isReady &= (Arrays.asList(validStates).contains(template.getState()));
            logger.trace("template state: {}", template.getState());
        }
        for (SnapshotDataStoreVO snapshot : snapshots) {
            isReady &= (Arrays.asList(validStates).contains(snapshot.getState()));
            logger.trace("snapshot state: {}", snapshot.getState());
        }
        for (VolumeDataStoreVO volume : volumes) {
            isReady &= (Arrays.asList(validStates).contains(volume.getState()));
            logger.trace("volume state: {}", volume.getState());
        }
        return isReady;
    }

    private boolean filesReadyToMigrate(Long srcDataStoreId) {
        List<TemplateDataStoreVO> templates = templateDataStoreDao.listByStoreId(srcDataStoreId);
        List<SnapshotDataStoreVO> snapshots = snapshotDataStoreDao.listByStoreId(srcDataStoreId, DataStoreRole.Image);
        List<VolumeDataStoreVO> volumes = volumeDataStoreDao.listByStoreId(srcDataStoreId);
        return filesReadyToMigrate(srcDataStoreId, templates, snapshots, volumes);
    }

    protected void checkIfCompleteMigrationPossible(ImageStoreService.MigrationPolicy policy, Long srcDataStoreId) {
        if (policy == ImageStoreService.MigrationPolicy.COMPLETE) {
            if (!filesReadyToMigrate(srcDataStoreId)) {
                throw new CloudRuntimeException("Complete migration failed as there are data objects which are not Ready - i.e, they may be in Migrating, creating, copying, etc. states");
            }
        }
        return;
    }

    protected Long getFileSize(DataObject file, Map<DataObject, Pair<List<SnapshotInfo>, Long>> snapshotChain, Map<DataObject, Pair<List<TemplateInfo>, Long>> templateChain) {
        Long size = file.getPhysicalSize();
        Pair<List<SnapshotInfo>, Long> chain = snapshotChain.get(file);
        Pair<List<TemplateInfo>, Long> tempChain = templateChain.get(file);

        if (file instanceof SnapshotInfo && chain.first() != null && !chain.first().isEmpty()) {
            size = chain.second();
        }
        if (file instanceof TemplateInfo && tempChain.first() != null && !tempChain.first().isEmpty()) {
            size = tempChain.second();
        }
        return size;
    }

    /**
     * Sorts the datastores in decreasing order of their free capacities, so as to make
     * an informed decision of picking the datastore with maximum free capactiy for migration
     */
    protected List<Long> sortDataStores(Map<Long, Pair<Long, Long>> storageCapacities) {
        List<Map.Entry<Long, Pair<Long, Long>>> list =
                new LinkedList<Map.Entry<Long, Pair<Long, Long>>>((storageCapacities.entrySet()));

        Collections.sort(list, new Comparator<Map.Entry<Long, Pair<Long, Long>>>() {
            @Override
            public int compare(Map.Entry<Long, Pair<Long, Long>> e1, Map.Entry<Long, Pair<Long, Long>> e2) {
                return e2.getValue().first() > e1.getValue().first() ? 1 : -1;
            }
        });
        HashMap<Long, Pair<Long, Long>> temp = new LinkedHashMap<>();
        for (Map.Entry<Long, Pair<Long, Long>> value : list) {
            temp.put(value.getKey(), value.getValue());
        }

        return new ArrayList<>(temp.keySet());
    }

    protected List<DataObject> getSortedValidSourcesList(DataStore srcDataStore, Map<DataObject, Pair<List<SnapshotInfo>, Long>> snapshotChains,
            Map<DataObject, Pair<List<TemplateInfo>, Long>> childTemplates, List<TemplateDataStoreVO> templates, List<SnapshotDataStoreVO> snapshots) {
        List<DataObject> files = new ArrayList<>();

        files.addAll(getAllReadyTemplates(srcDataStore, childTemplates, templates));
        files.addAll(getAllReadySnapshotsAndChains(srcDataStore, snapshotChains, snapshots));

        files = sortFilesOnSize(files, snapshotChains);

        return files;
    }

    protected List<DataObject> getSortedValidSourcesList(DataStore srcDataStore, Map<DataObject, Pair<List<SnapshotInfo>, Long>> snapshotChains,
            Map<DataObject, Pair<List<TemplateInfo>, Long>> childTemplates) {
        List<DataObject> files = new ArrayList<>();
        files.addAll(getAllReadyTemplates(srcDataStore, childTemplates));
        files.addAll(getAllReadySnapshotsAndChains(srcDataStore, snapshotChains));
        files.addAll(getAllReadyVolumes(srcDataStore));

        files = sortFilesOnSize(files, snapshotChains);

        return files;
    }

    protected List<DataObject> sortFilesOnSize(List<DataObject> files, Map<DataObject, Pair<List<SnapshotInfo>, Long>> snapshotChains) {
        Collections.sort(files, new Comparator<DataObject>() {
            @Override
            public int compare(DataObject o1, DataObject o2) {
                Long size1 = o1.getPhysicalSize();
                Long size2 = o2.getPhysicalSize();
                if (o1 instanceof SnapshotInfo) {
                    size1 = snapshotChains.get(o1).second();
                }
                if (o2 instanceof  SnapshotInfo) {
                    size2 = snapshotChains.get(o2).second();
                }
                return size2 > size1 ? 1 : -1;
            }
        });
        return files;
    }

    protected List<DataObject> getAllReadyTemplates(DataStore srcDataStore, Map<DataObject, Pair<List<TemplateInfo>, Long>> childTemplates, List<TemplateDataStoreVO> templates) {
        List<TemplateInfo> files = new LinkedList<>();
        for (TemplateDataStoreVO template : templates) {
            VMTemplateVO templateVO = templateDao.findById(template.getTemplateId());
            if (shouldMigrateTemplate(template, templateVO)) {
                files.add(templateFactory.getTemplate(template.getTemplateId(), srcDataStore));
            }
        }
        for (TemplateInfo template: files) {
            List<VMTemplateVO> children = templateDao.listByParentTemplatetId(template.getId());
            List<TemplateInfo> temps = new ArrayList<>();
            temps.add(template);
            for(VMTemplateVO child : children) {
                temps.add(templateFactory.getTemplate(child.getId(), srcDataStore));
            }
            childTemplates.put(template, new Pair<>(temps, getTotalChainSize(temps)));
        }
        return (List<DataObject>) (List<?>) files;
    }

    protected List<DataObject> getAllReadyTemplates(DataStore srcDataStore, Map<DataObject, Pair<List<TemplateInfo>, Long>> childTemplates) {
        List<TemplateDataStoreVO> templates = templateDataStoreDao.listByStoreId(srcDataStore.getId());
        return getAllReadyTemplates(srcDataStore, childTemplates, templates);
    }

    /**
     * Returns whether a template should be migrated. A template should be migrated if:
     * <ol>
     * <li>its state is ready, and</li>
     * <li>its hypervisor type is not simulator, and</li>
     * <li>it is not a child template.</li>
     * </ol>
     */
    protected boolean shouldMigrateTemplate(TemplateDataStoreVO template, VMTemplateVO templateVO) {
        if (template.getState() != State.Ready) {
            logger.debug("Template [{}] should not be migrated as it is not ready.", template);
            return false;
        }

        if (templateVO.getHypervisorType() == Hypervisor.HypervisorType.Simulator) {
            logger.debug("Template [{}] should not be migrated as its hypervisor type is simulator.", template);
            return false;
        }

        if (templateVO.getParentTemplateId() != null) {
            logger.debug("Template [{}] should not be migrated as it has a parent template.", template);
            return false;
        }

        logger.debug("Template [{}] should be migrated.", template);
        return true;
    }

    /** Returns parent snapshots and snapshots that do not have any children; snapshotChains comprises of the snapshot chain info
     * for each parent snapshot and the cumulative size of the chain - this is done to ensure that all the snapshots in a chain
     * are migrated to the same datastore
     */
    protected List<DataObject> getAllReadySnapshotsAndChains(DataStore srcDataStore, Map<DataObject, Pair<List<SnapshotInfo>, Long>> snapshotChains, List<SnapshotDataStoreVO> snapshots) {
        List<SnapshotInfo> files = new LinkedList<>();
        for (SnapshotDataStoreVO snapshot : snapshots) {
            SnapshotVO snapshotVO = snapshotDao.findById(snapshot.getSnapshotId());
            if (snapshot.getState() == ObjectInDataStoreStateMachine.State.Ready &&
                    snapshotVO != null && snapshotVO.getHypervisorType() != Hypervisor.HypervisorType.Simulator
                    && snapshot.getParentSnapshotId() == 0 ) {
                SnapshotInfo snap = snapshotFactory.getSnapshot(snapshotVO.getSnapshotId(), snapshot.getDataStoreId(), snapshot.getRole());
                if (snap != null) {
                    files.add(snap);
                }
            }
        }

        for (SnapshotInfo parent : files) {
            List<SnapshotInfo> chain = new ArrayList<>();
            chain.add(parent);
            for (int i =0; i< chain.size(); i++) {
                SnapshotInfo child = chain.get(i);
                List<SnapshotInfo> children = child.getChildren();
                if (children != null) {
                    chain.addAll(children);
                }
            }
            snapshotChains.put(parent, new Pair<List<SnapshotInfo>, Long>(chain, getTotalChainSize(chain)));
        }

        return (List<DataObject>) (List<?>) files;
    }

    protected List<DataObject> getAllReadySnapshotsAndChains(DataStore srcDataStore, Map<DataObject, Pair<List<SnapshotInfo>, Long>> snapshotChains) {
        List<SnapshotDataStoreVO> snapshots = snapshotDataStoreDao.listByStoreId(srcDataStore.getId(), DataStoreRole.Image);
        return getAllReadySnapshotsAndChains(srcDataStore, snapshotChains, snapshots);
    }

    protected Long getTotalChainSize(List<? extends DataObject> chain) {
        Long size = 0L;
        for (DataObject dataObject : chain) {
            size += dataObject.getPhysicalSize();
        }
        return size;
    }

    protected List<DataObject> getAllReadyVolumes(DataStore srcDataStore, List<VolumeDataStoreVO> volumes) {
        List<DataObject> files = new LinkedList<>();
        for (VolumeDataStoreVO volume : volumes) {
            if (volume.getState() == ObjectInDataStoreStateMachine.State.Ready) {
                VolumeInfo volumeInfo = volumeFactory.getVolume(volume.getVolumeId(), srcDataStore);
                if (volumeInfo != null && volumeInfo.getHypervisorType() != Hypervisor.HypervisorType.Simulator) {
                    files.add(volumeInfo);
                }
            }
        }
        return files;
    }

    protected List<DataObject> getAllReadyVolumes(DataStore srcDataStore) {
        List<VolumeDataStoreVO> volumes = volumeDataStoreDao.listByStoreId(srcDataStore.getId());
        return getAllReadyVolumes(srcDataStore, volumes);
    }

    /** Returns the count of active SSVMs - SSVM with agents in connected state, so as to dynamically increase the thread pool
     * size when SSVMs scale
     */
    protected int activeSSVMCount(DataStore dataStore) {
        long datacenterId = dataStore.getScope().getScopeId();
        List<SecondaryStorageVmVO> ssvms =
                secStorageVmDao.getSecStorageVmListInStates(null, datacenterId, VirtualMachine.State.Running, VirtualMachine.State.Migrating);
        int activeSSVMs = 0;
        for (SecondaryStorageVmVO vm : ssvms) {
            String name = "s-"+vm.getId()+"-VM";
            HostVO ssHost = hostDao.findByName(name);
            if (ssHost != null) {
                if (ssHost.getState() == Status.Up) {
                    activeSSVMs++;
                }
            }
        }
        return activeSSVMs;
    }
}
