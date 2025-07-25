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

<template>
  <a-spin :spinning="loading">
    <div v-if="!isNormalUserOrProject">
      <ownership-selection @fetch-owner="fetchOwnerOptions" />
    </div>
    <a-form
      class="form"
      layout="vertical"
      :ref="formRef"
      :model="form"
      :rules="rules"
      @finish="handleSubmit"
      v-ctrl-enter="handleSubmit"
     >
      <a-form-item ref="name" name="name">
        <template #label>
          <tooltip-label :title="$t('label.name')" :tooltip="apiParams.name.description"/>
        </template>
        <a-input
          v-focus="true"
          v-model:value="form.name"
          :placeholder="apiParams.name.description" />
      </a-form-item>
      <a-form-item ref="zoneid" name="zoneid" v-if="!createVolumeFromVM">
        <template #label>
          <tooltip-label :title="$t('label.zoneid')" :tooltip="apiParams.zoneid.description"/>
        </template>
        <a-select
          v-model:value="form.zoneid"
          :loading="loading"
          @change="zone => fetchDiskOfferings(zone)"
          :placeholder="apiParams.zoneid.description"
          showSearch
          optionFilterProp="label"
          :filterOption="(input, option) => {
            return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
          }" >
          <a-select-option
            v-for="(zone, index) in zones"
            :value="zone.id"
            :key="index"
            :label="zone.name">
            <span>
              <resource-icon v-if="zone.icon" :image="zone.icon.base64image" size="1x" style="margin-right: 5px"/>
              <global-outlined v-else style="margin-right: 5px"/>
              {{ zone.name }}
            </span>
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item ref="diskofferingid" name="diskofferingid" v-if="!createVolumeFromSnapshot || (createVolumeFromSnapshot && resource.volumetype === 'ROOT')">
        <template #label>
          <tooltip-label :title="$t('label.diskofferingid')" :tooltip="apiParams.diskofferingid.description || 'Disk Offering'"/>
        </template>
        <a-select
          v-model:value="form.diskofferingid"
          :loading="loading"
          @change="id => onChangeDiskOffering(id)"
          :placeholder="apiParams.diskofferingid.description || $t('label.diskofferingid')"
          showSearch
          optionFilterProp="label"
          :filterOption="(input, option) => {
            return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
          }" >
          <a-select-option
            v-for="(offering, index) in offerings"
            :value="offering.id"
            :key="index"
            :label="offering.displaytext || offering.name">
            {{ offering.displaytext || offering.name }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <span v-if="customDiskOffering">
        <a-form-item ref="size" name="size">
          <template #label>
            <tooltip-label :title="$t('label.sizegb')" :tooltip="apiParams.size.description"/>
          </template>
          <a-input
            v-model:value="form.size"
            :placeholder="apiParams.size.description"/>
        </a-form-item>
      </span>
      <span v-if="isCustomizedDiskIOps">
        <a-form-item ref="miniops" name="miniops">
          <template #label>
            <tooltip-label :title="$t('label.miniops')" :tooltip="apiParams.miniops.description"/>
          </template>
          <a-input
            v-model:value="form.miniops"
            :placeholder="apiParams.miniops.description"/>
        </a-form-item>
        <a-form-item ref="maxiops" name="maxiops">
          <template #label>
            <tooltip-label :title="$t('label.maxiops')" :tooltip="apiParams.maxiops.description"/>
          </template>
          <a-input
            v-model:value="form.maxiops"
            :placeholder="apiParams.maxiops.description"/>
        </a-form-item>
      </span>
      <a-form-item name="attachVolume" ref="attachVolume" v-if="!createVolumeFromVM">
        <template #label>
          <tooltip-label :title="$t('label.action.attach.to.instance')" :tooltip="$t('label.attach.vol.to.instance')" />
        </template>
        <a-switch v-model:checked="form.attachVolume" :checked="attachVolume" @change="zone => onChangeAttachToVM(zone.id)" />
      </a-form-item>
      <span v-if="attachVolume">
        <a-form-item :label="$t('label.virtualmachineid')" name="virtualmachineid" ref="virtualmachineid">
          <a-select
            v-focus="true"
            v-model:value="form.virtualmachineid"
            :placeholder="attachVolumeApiParams.virtualmachineid.description"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => {
              return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }" >
            <a-select-option v-for="vm in virtualmachines" :key="vm.id" :label="vm.name || vm.displayname">
              {{ vm.name || vm.displayname }}
            </a-select-option>
          </a-select>
        </a-form-item >
        <a-form-item :label="$t('label.deviceid')">
          <div style="margin-bottom: 10px">
            <a-collapse>
              <a-collapse-panel header="More information about deviceID">
                <a-alert type="warning">
                  <template #message>
                    <span v-html="attachVolumeApiParams.deviceid.description" />
                  </template>
                </a-alert>
              </a-collapse-panel>
            </a-collapse>
          </div>
          <a-input-number
            v-model:value="form.deviceid"
            style="width: 100%;"
            :min="0"
            :placeholder="$t('label.deviceid')"
          />
        </a-form-item>
      </span>
      <div :span="24" class="action-button">
        <a-button @click="closeModal">{{ $t('label.cancel') }}</a-button>
        <a-button type="primary" ref="submit" @click="handleSubmit">{{ $t('label.ok') }}</a-button>
      </div>
    </a-form>
  </a-spin>
</template>

<script>
import { ref, reactive, toRaw } from 'vue'
import { getAPI, postAPI } from '@/api'
import { mixinForm } from '@/utils/mixin'
import ResourceIcon from '@/components/view/ResourceIcon'
import TooltipLabel from '@/components/widgets/TooltipLabel'
import OwnershipSelection from '@/views/compute/wizard/OwnershipSelection.vue'
import store from '@/store'

export default {
  name: 'CreateVolume',
  mixins: [mixinForm],
  components: {
    OwnershipSelection,
    ResourceIcon,
    TooltipLabel
  },
  props: {
    resource: {
      type: Object,
      default: () => {}
    }
  },
  data () {
    return {
      owner: {
        projectid: store.getters.project?.id,
        domainid: store.getters.project?.id ? null : store.getters.userInfo.domainid,
        account: store.getters.project?.id ? null : store.getters.userInfo.account
      },
      snapshotZoneIds: [],
      zones: [],
      offerings: [],
      customDiskOffering: false,
      loading: false,
      isCustomizedDiskIOps: false,
      virtualmachines: [],
      attachVolume: false,
      vmidtoattach: null
    }
  },
  computed: {
    createVolumeFromVM () {
      return this.$route.path.startsWith('/vm/')
    },
    isNormalUserOrProject () {
      return ['User'].includes(this.$store.getters.userInfo.roletype) || store.getters.project?.id
    },
    createVolumeFromSnapshot () {
      return this.$route.path.startsWith('/snapshot')
    }
  },
  beforeCreate () {
    this.apiParams = this.$getApiParams('createVolume')
  },
  created () {
    this.initForm()
    this.fetchData()
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({})
      this.rules = reactive({
        zoneid: [{ required: true, message: this.$t('message.error.zone') }],
        size: [{ required: true, message: this.$t('message.error.custom.disk.size') }],
        miniops: [{
          validator: async (rule, value) => {
            if (value && (isNaN(value) || value <= 0)) {
              return Promise.reject(this.$t('message.error.number'))
            }
            return Promise.resolve()
          }
        }],
        maxiops: [{
          validator: async (rule, value) => {
            if (value && (isNaN(value) || value <= 0)) {
              return Promise.reject(this.$t('message.error.number'))
            }
            return Promise.resolve()
          }
        }]
      })
      if (this.attachVolume) {
        this.rules.virtualmachineid = [{ required: true, message: this.$t('message.error.select') }]
        this.rules.deviceid = [{ required: true, message: this.$t('message.error.select') }]
      }
      if (!this.createVolumeFromSnapshot) {
        this.rules.name = [{ required: true, message: this.$t('message.error.volume.name') }]
        this.rules.diskofferingid = [{ required: true, message: this.$t('message.error.select') }]
      }
    },
    fetchOwnerOptions (OwnerOptions) {
      this.owner = {}
      if (OwnerOptions.selectedAccountType === 'Account') {
        if (!OwnerOptions.selectedAccount) {
          return
        }
        this.owner.account = OwnerOptions.selectedAccount
        this.owner.domainid = OwnerOptions.selectedDomain
      } else if (OwnerOptions.selectedAccountType === 'Project') {
        if (!OwnerOptions.selectedProject) {
          return
        }
        this.owner.projectid = OwnerOptions.selectedProject
      }
      this.fetchData()
    },
    fetchData () {
      if (this.createVolumeFromSnapshot) {
        this.fetchSnapshotZones()
        return
      }
      let zoneId = null
      if (this.createVolumeFromVM) {
        zoneId = this.resource.zoneid
      }
      this.fetchZones(zoneId)
    },
    fetchZones (id) {
      this.loading = true
      const params = { showicon: true }
      if (Array.isArray(id)) {
        params.ids = id.join()
      } else if (id !== null) {
        params.id = id
      }
      getAPI('listZones', params).then(json => {
        this.zones = json.listzonesresponse.zone || []
        this.form.zoneid = this.zones[0].id || ''
        this.fetchDiskOfferings(this.form.zoneid)
        if (this.attachVolume) {
          this.fetchVirtualMachines(this.form.zoneid)
        }
      }).finally(() => {
        this.loading = false
      })
    },
    fetchSnapshotZones () {
      this.loading = true
      this.snapshotZoneIds = []
      const params = {
        showunique: false,
        id: this.resource.id
      }
      getAPI('listSnapshots', params).then(json => {
        const snapshots = json.listsnapshotsresponse.snapshot || []
        for (const snapshot of snapshots) {
          if (!this.snapshotZoneIds.includes(snapshot.zoneid)) {
            this.snapshotZoneIds.push(snapshot.zoneid)
          }
        }
      }).finally(() => {
        if (this.snapshotZoneIds && this.snapshotZoneIds.length > 0) {
          this.fetchZones(this.snapshotZoneIds)
        }
      })
    },
    fetchDiskOfferings (zoneId) {
      this.loading = true
      var params = {
        zoneid: zoneId,
        listall: true,
        domainid: this.owner.domainid
      }
      if (this.createVolumeFromVM) {
        params.virtualmachineid = this.resource.id
      }
      if (this.owner.projectid) {
        params.projectid = this.owner.projectid
      } else {
        params.account = this.owner.account
      }
      getAPI('listDiskOfferings', params).then(json => {
        this.offerings = json.listdiskofferingsresponse.diskoffering || []
        if (this.createVolumeFromVM) {
          this.offerings = this.offerings.filter(x => x.suitableforvirtualmachine)
        }
        if (!this.createVolumeFromSnapshot) {
          this.form.diskofferingid = this.offerings[0].id || ''
        }
        this.customDiskOffering = this.offerings[0].iscustomized || false
        this.isCustomizedDiskIOps = this.offerings[0]?.iscustomizediops || false
      }).finally(() => {
        this.loading = false
      })
    },
    fetchVirtualMachines (zoneId) {
      var params = {
        zoneid: zoneId,
        details: 'min'
      }
      if (this.owner.projectid) {
        params.projectid = this.owner.projectid
      } else {
        params.account = this.owner.account
        params.domainid = this.owner.domainid
      }

      this.loading = true
      var vmStates = ['Running', 'Stopped']
      vmStates.forEach((state) => {
        params.state = state
        getAPI('listVirtualMachines', params).then(response => {
          this.virtualmachines = this.virtualmachines.concat(response.listvirtualmachinesresponse.virtualmachine || [])
        }).catch(error => {
          this.$notifyError(error)
        }).finally(() => {
          this.loading = false
        })
      })
    },
    handleSubmit (e) {
      if (this.loading) return
      this.formRef.value.validate().then(() => {
        const formRaw = toRaw(this.form)
        const values = this.handleRemoveFields(formRaw)
        if (this.createVolumeFromVM) {
          values.account = this.resource.account
          values.domainid = this.resource.domainid
          values.virtualmachineid = this.resource.id
          values.zoneid = this.resource.zoneid
        }
        if (this.customDiskOffering) {
          values.size = values.size.trim()
        }
        if (this.createVolumeFromSnapshot) {
          values.snapshotid = this.resource.id
        }
        if (this.attachVolume) {
          this.vmidtoattach = values.virtualmachineid
          values.virtualmachineid = null
        }
        values.domainid = this.owner.domainid
        if (this.owner.projectid) {
          values.projectid = this.owner.projectid
        } else {
          values.account = this.owner.account
        }
        this.loading = true
        postAPI('createVolume', values).then(response => {
          this.$pollJob({
            jobId: response.createvolumeresponse.jobid,
            title: this.$t('message.success.create.volume'),
            description: values.name,
            successMessage: this.$t('message.success.create.volume'),
            successMethod: (result) => {
              this.closeModal()
              if (this.createVolumeFromVM || this.attachVolume) {
                const params = {}
                params.id = result.jobresult.volume.id
                if (this.createVolumeFromVM) {
                  params.virtualmachineid = this.resource.id
                } else {
                  params.virtualmachineid = this.vmidtoattach
                  params.deviceid = values.deviceid
                }
                postAPI('attachVolume', params).then(response => {
                  this.$pollJob({
                    jobId: response.attachvolumeresponse.jobid,
                    title: this.$t('message.success.attach.volume'),
                    description: values.name,
                    successMessage: this.$t('message.attach.volume.success'),
                    errorMessage: this.$t('message.attach.volume.failed'),
                    loadingMessage: this.$t('message.attach.volume.progress'),
                    catchMessage: this.$t('error.fetching.async.job.result')
                  })
                })
              }
            },
            errorMessage: this.$t('message.create.volume.failed'),
            loadingMessage: this.$t('message.create.volume.processing'),
            catchMessage: this.$t('error.fetching.async.job.result')
          })
          this.closeModal()
        }).catch(error => {
          this.$notifyError(error)
        }).finally(() => {
          this.loading = false
        })
      }).catch((error) => {
        this.formRef.value.scrollToField(error.errorFields[0].name)
      })
    },
    closeModal () {
      this.$emit('close-action')
    },
    onChangeDiskOffering (id) {
      const offering = this.offerings.filter(x => x.id === id)
      this.customDiskOffering = offering[0]?.iscustomized || false
      this.isCustomizedDiskIOps = offering[0]?.iscustomizediops || false
    },
    onChangeAttachToVM (zone) {
      this.attachVolume = !this.attachVolume
      this.virtualmachines = []
      if (this.attachVolume) {
        this.attachVolumeApiParams = this.$getApiParams('attachVolume')
        this.fetchVirtualMachines(this.form.zoneid)
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.form {
  width: 80vw;

  @media (min-width: 500px) {
    min-width: 400px;
    width: 100%;
  }
}
</style>
