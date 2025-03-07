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
package com.cloud.storage;

import java.util.Date;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.TableGenerator;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;

import com.cloud.util.StoragePoolTypeConverter;
import org.apache.cloudstack.utils.reflectiontostringbuilderutils.ReflectionToStringBuilderUtils;

import com.cloud.storage.Storage.ProvisioningType;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.db.GenericDao;

@Entity
@Table(name = "volumes")
public class VolumeVO implements Volume {
    @Id
    @TableGenerator(name = "volume_sq", table = "sequence", pkColumnName = "name", valueColumnName = "value", pkColumnValue = "volume_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.TABLE)
    @Column(name = "id")
    private long id;

    @Column(name = "last_id")
    private long lastId;

    @Column(name = "name")
    private String name;

    @Column(name = "pool_id")
    private Long poolId;

    @Column(name = "last_pool_id")
    private Long lastPoolId;

    @Column(name = "account_id")
    private long accountId;

    @Column(name = "domain_id")
    private long domainId;

    @Column(name = "instance_id")
    private Long instanceId = null;

    @Column(name = "device_id")
    private Long deviceId = null;

    @Column(name = "size")
    private Long size;

    @Column(name = "min_iops")
    private Long minIops;

    @Column(name = "max_iops")
    private Long maxIops;

    @Column(name = "folder")
    private String folder;

    @Column(name = "path")
    private String path;

    @Column(name = "pod_id")
    private Long podId;

    @Column(name = "created")
    private Date created;

    @Column(name = "attached")
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date attached;

    @Column(name = "data_center_id")
    private long dataCenterId;

    @Column(name = "host_ip")
    private String hostIp;

    @Column(name = "disk_offering_id")
    private long diskOfferingId;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "first_snapshot_backup_uuid")
    private String firstSnapshotBackupUuid;

    @Column(name = "volume_type")
    @Enumerated(EnumType.STRING)
    private Type volumeType = Volume.Type.UNKNOWN;

    @Column(name = "pool_type")
    @Convert(converter = StoragePoolTypeConverter.class)
    private StoragePoolType poolType;

    @Column(name = GenericDao.REMOVED_COLUMN)
    private Date removed;

    @Column(name = "updated")
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date updated;

    @Column(name = "update_count", updatable = true, nullable = false)
    protected long updatedCount; // This field should be updated everytime the
                                 // state is updated. There's no set method in
                                 // the vo object because it is done with in the
                                 // dao code.

    @Column(name = "recreatable")
    private boolean recreatable;

    @Column(name = "state")
    @Enumerated(value = EnumType.STRING)
    private State state;

    @Column(name = "chain_info", length = 65535)
    private String chainInfo;

    @Column(name = "uuid")
    private String uuid;

    @Column(name = "format")
    private Storage.ImageFormat format;

    @Column(name = "provisioning_type")
    private Storage.ProvisioningType provisioningType;

    @Column(name = "display_volume", updatable = true, nullable = false)
    protected boolean displayVolume = true;

    @Column(name = "iscsi_name")
    private String _iScsiName;

    @Column(name = "vm_snapshot_chain_size")
    private Long vmSnapshotChainSize;

    @Column(name = "iso_id")
    private Long isoId;

    @Column(name = "external_uuid")
    private String externalUuid = null;

    @Transient
    // @Column(name="reservation")
    private String reservationId;

    @Column(name = "hv_ss_reserve")
    private Integer hypervisorSnapshotReserve;

    @Transient
    private boolean deployAsIs;

    @Column(name = "passphrase_id")
    private Long passphraseId;

    @Column(name = "encrypt_format")
    private String encryptFormat;

    @Column(name = "delete_protection")
    private boolean deleteProtection;


    // Real Constructor
    public VolumeVO(Type type, String name, long dcId, long domainId,
            long accountId, long diskOfferingId, Storage.ProvisioningType provisioningType, long size,
            Long minIops, Long maxIops, String iScsiName) {
        this.volumeType = type;
        this.name = name;
        dataCenterId = dcId;
        this.accountId = accountId;
        this.domainId = domainId;
        this.provisioningType = provisioningType;
        this.size = size;
        this.minIops = minIops;
        this.maxIops = maxIops;
        _iScsiName = iScsiName;
        this.diskOfferingId = diskOfferingId;
        state = State.Allocated;
        uuid = UUID.randomUUID().toString();
    }

    public VolumeVO(String name, long dcId, Long podId, long accountId,
            long domainId, Long instanceId, String folder, String path, Storage.ProvisioningType provisioningType,
            long size, Long minIops, Long maxIops, String iScsiName,
            Volume.Type vType) {
        this.name = name;
        this.accountId = accountId;
        this.domainId = domainId;
        this.instanceId = instanceId;
        this.folder = folder;
        this.path = path;
        this.provisioningType = provisioningType;
        this.size = size;
        this.minIops = minIops;
        this.maxIops = maxIops;
        _iScsiName = iScsiName;
        this.podId = podId;
        dataCenterId = dcId;
        volumeType = vType;
        state = Volume.State.Allocated;
        recreatable = false;
        uuid = UUID.randomUUID().toString();
    }

    public VolumeVO(String name, long dcId, long podId, long accountId,
            long domainId, Long instanceId, String folder, String path, Storage.ProvisioningType provisioningType,
            long size, Volume.Type vType) {
        this.name = name;
        this.accountId = accountId;
        this.domainId = domainId;
        this.instanceId = instanceId;
        this.folder = folder;
        this.path = path;
        this.provisioningType = provisioningType;
        this.size = size;
        minIops = null;
        maxIops = null;
        _iScsiName = null;
        this.podId = podId;
        dataCenterId = dcId;
        volumeType = vType;
        state = Volume.State.Allocated;
        recreatable = false;
        uuid = UUID.randomUUID().toString();
    }

    // Copy Constructor
    public VolumeVO(Volume that) {
        this(that.getName(),
            that.getDataCenterId(),
            that.getPodId(),
            that.getAccountId(),
            that.getDomainId(),
            that.getInstanceId(),
            that.getFolder(),
            that.getPath(),
            that.getProvisioningType(),
            that.getSize(),
            that.getMinIops(),
            that.getMaxIops(),
            that.get_iScsiName(),
            that.getVolumeType());
        recreatable = that.isRecreatable();
        state = State.Allocated; //This should be in Allocated state before going into Ready state
        size = that.getSize();
        minIops = that.getMinIops();
        maxIops = that.getMaxIops();
        _iScsiName = that.get_iScsiName();
        diskOfferingId = that.getDiskOfferingId();
        poolId = that.getPoolId();
        attached = that.getAttached();
        chainInfo = that.getChainInfo();
        templateId = that.getTemplateId();
        deviceId = that.getDeviceId();
        format = that.getFormat();
        provisioningType = that.getProvisioningType();
        uuid = UUID.randomUUID().toString();
        deployAsIs = that.isDeployAsIs();
        externalUuid = that.getExternalUuid();
    }

    @Override
    public long getUpdatedCount() {
        return updatedCount;
    }

    @Override
    public void incrUpdatedCount() {
        updatedCount++;
    }

    public void decrUpdatedCount() {
        updatedCount--;
    }

    @Override
    public boolean isRecreatable() {
        return recreatable;
    }

    public void setRecreatable(boolean recreatable) {
        this.recreatable = recreatable;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public Long getPodId() {
        return podId;
    }

    @Override
    public long getDataCenterId() {
        return dataCenterId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getAccountId() {
        return accountId;
    }

    public void setPoolType(StoragePoolType poolType) {
        this.poolType = poolType;
    }

    public StoragePoolType getPoolType() { return poolType; }

    @Override
    public long getDomainId() {
        return domainId;
    }

    @Override
    public String getFolder() {
        return folder;
    }

    @Override
    public String getPath() {
        return path;
    }

    protected VolumeVO() {
    }

    @Override
    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    @Override
    public Long getMinIops() {
        return minIops;
    }

    public void setMinIops(Long minIops) {
        this.minIops = minIops;
    }

    @Override
    public Long getMaxIops() {
        return maxIops;
    }

    public void setMaxIops(Long maxIops) {
        this.maxIops = maxIops;
    }

    @Override
    public Long getInstanceId() {
        return instanceId;
    }

    @Override
    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public Type getVolumeType() {
        return volumeType;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    public void setDomainId(long domainId) {
        this.domainId = domainId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getHostIp() {
        return hostIp;
    }

    public void setHostIp(String hostip) {
        this.hostIp = hostip;
    }

    public void setPodId(Long podId) {
        this.podId = podId;
    }

    public void setDataCenterId(long dataCenterId) {
        this.dataCenterId = dataCenterId;
    }

    public void setVolumeType(Type type) {
        volumeType = type;
    }

    @Override
    public Date getCreated() {
        return created;
    }

    public Date getRemoved() {
        return removed;
    }

    public void setRemoved(Date removed) {
        this.removed = removed;
    }

    @Override
    public Long getDiskOfferingId() {
        return diskOfferingId;
    }

    public void setDiskOfferingId(long diskOfferingId) {
        this.diskOfferingId = diskOfferingId;
    }

    @Override
    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public String getFirstSnapshotBackupUuid() {
        return firstSnapshotBackupUuid;
    }

    public void setFirstSnapshotBackupUuid(String firstSnapshotBackupUuid) {
        this.firstSnapshotBackupUuid = firstSnapshotBackupUuid;
    }

    @Override
    public Long getPoolId() {
        return poolId;
    }

    public void setPoolId(Long poolId) {
        this.poolId = poolId;
    }

    @Override
    public Date getUpdated() {
        return updated;
    }

    @Override
    public State getState() {
        return state;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    @Override
    public String toString() {
        return String.format("Volume %s",
                ReflectionToStringBuilderUtils.reflectOnlySelectedFields(
                        this, "id", "uuid", "name", "volumeType", "instanceId"));
    }

    @Override
    public Date getAttached() {
        return attached;
    }

    public void setAttached(Date attached) {
        this.attached = attached;
    }

    @Override
    public String getChainInfo() {
        return chainInfo;
    }

    public void setChainInfo(String chainInfo) {
        this.chainInfo = chainInfo;
    }

    public Long getLastPoolId() {
        return lastPoolId;
    }

    public void setLastPoolId(Long poolId) {
        lastPoolId = poolId;
    }

    @Override
    public int hashCode() {
        return NumbersUtil.hash(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof VolumeVO) {
            return id == ((VolumeVO)obj).id;
        } else {
            return false;
        }
    }

    @Override
    public String getReservationId() {
        return reservationId;
    }

    @Override
    public void setReservationId(String reserv) {
        reservationId = reserv;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String get_iScsiName() {
        return _iScsiName;
    }

    public void set_iScsiName(String iScsiName) {
        _iScsiName = iScsiName;
    }

    @Override
    public boolean isDisplayVolume() {
        return displayVolume;
    }

    @Override
    public  boolean isDisplay(){
        return displayVolume;
    }

    @Override
    public boolean isDeployAsIs() {
        return deployAsIs;
    }

    public void setDisplay(boolean display){
        this.displayVolume = display;
    }

    public void setDisplayVolume(boolean displayVolume) {
        this.displayVolume = displayVolume;
    }

    @Override
    public Storage.ImageFormat getFormat() {
        return format;
    }

    public void setFormat(Storage.ImageFormat format) {
        this.format = format;
    }

    @Override
    public ProvisioningType getProvisioningType(){
        return provisioningType;
    }

    public void setProvisioningType(ProvisioningType provisioningType){
        this.provisioningType = provisioningType;
    }

    public void setVmSnapshotChainSize(Long vmSnapshotChainSize){
        this.vmSnapshotChainSize = vmSnapshotChainSize;
    }

    @Override
    public Long getVmSnapshotChainSize() {
        return vmSnapshotChainSize;
    }

    public Long getIsoId() {
        return isoId;
    }

    public void setIsoId(Long isoId) {
        this.isoId = isoId;
    }

    // don't use this directly, use volume state machine instead
    // This method is used by UpdateVolume as a part of "Better control over first class objects in CS"
    public void setState(State state) {
        this.state = state;
    }

    public void setHypervisorSnapshotReserve(Integer hypervisorSnapshotReserve) {
        this.hypervisorSnapshotReserve = hypervisorSnapshotReserve;
    }

    @Override
    public Integer getHypervisorSnapshotReserve() {
        return hypervisorSnapshotReserve;
    }

    @Override
    public Class<?> getEntityType() {
        return Volume.class;
    }

    public String getVolumeDescription(){
        return ReflectionToStringBuilderUtils.reflectOnlySelectedFields(this, "name", "uuid");
    }

    @Override
    public String getExternalUuid() {
        return externalUuid;
    }

    @Override
    public void setExternalUuid(String externalUuid) {
        this.externalUuid = externalUuid;
    }

    public Long getPassphraseId() { return passphraseId; }

    public void setPassphraseId(Long id) { this.passphraseId = id; }

    public String getEncryptFormat() { return encryptFormat; }

    public void setEncryptFormat(String encryptFormat) { this.encryptFormat = encryptFormat; }

    @Override
    public boolean isDeleteProtection() {
        return deleteProtection;
    }

    public void setDeleteProtection(boolean deleteProtection) {
        this.deleteProtection = deleteProtection;
    }

    public long getLastId() {
        return lastId;
    }

    public void setLastId(long lastId) {
        this.lastId = lastId;
    }
}
