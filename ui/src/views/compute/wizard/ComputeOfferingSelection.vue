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
  <div>
    <a-input-search
      style="width: 25vw;float: right;margin-bottom: 10px; z-index: 8"
      :placeholder="$t('label.search')"
      v-model:value="filter"
      @search="handleSearch" />
    <a-table
      :columns="columns"
      :dataSource="tableSource"
      :pagination="false"
      :rowSelection="rowSelection"
      :customRow="onClickRow"
      :loading="loading"
      size="middle"
      :scroll="{ y: 225 }"
    >
      <template #headerCell="{ column }">
        <template v-if="column.key === 'cpu'"><appstore-outlined /> {{ $t('label.cpu') }}</template>
        <template v-if="column.key === 'ram'"><bulb-outlined /> {{ $t('label.memory') }}</template>
      </template>
      <template #displayText="{ record }">
        <span>{{ record.name }}</span>
        <span
            v-if="record.leaseduration !== undefined"
            :style="{
              'margin-right': '10px',
              'float': 'right'}">
          <a-tooltip>
            <template #title>{{ $t('label.remainingdays')  + ": " + getRemainingLeaseText(record.leaseduration) }}</template>
            <field-time-outlined
              :style="{
                color: $store.getters.darkMode ? { color: 'rgba(255, 255, 255, 0.65)' } : { color: '#888' },
                fontSize: '20px'
              }"/>
          </a-tooltip>
        </span>
      </template>
    </a-table>

    <div style="display: block; text-align: right;">
      <a-pagination
        size="small"
        :current="options.page"
        :pageSize="options.pageSize"
        :total="rowCount"
        :showTotal="total => `${$t('label.total')} ${total} ${$t('label.items')}`"
        :pageSizeOptions="['10', '20', '40', '80', '100', '200']"
        @change="onChangePage"
        @showSizeChange="onChangePageSize"
        showSizeChanger>
        <template #buildOptionText="props">
          <span>{{ props.value }} / {{ $t('label.page') }}</span>
        </template>
      </a-pagination>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ComputeOfferingSelection',
  props: {
    computeItems: {
      type: Array,
      default: () => []
    },
    selectedTemplate: {
      type: Object,
      default: () => {}
    },
    rowCount: {
      type: Number,
      default: () => 0
    },
    value: {
      type: String,
      default: ''
    },
    loading: {
      type: Boolean,
      default: false
    },
    preFillContent: {
      type: Object,
      default: () => {}
    },
    zoneId: {
      type: String,
      default: () => ''
    },
    autoscale: {
      type: Boolean,
      default: () => false
    },
    minimumCpunumber: {
      type: Number,
      default: 0
    },
    minimumCpuspeed: {
      type: Number,
      default: 0
    },
    minimumMemory: {
      type: Number,
      default: 0
    },
    allowAllOfferings: {
      type: Boolean,
      required: false,
      default: false
    }
  },
  data () {
    return {
      filter: '',
      columns: [
        {
          key: 'name',
          dataIndex: 'name',
          title: this.$t('label.serviceofferingid'),
          width: '40%',
          slots: { customRender: 'displayText' }
        },
        {
          key: 'cpu',
          dataIndex: 'cpu',
          width: '30%'
        },
        {
          key: 'ram',
          dataIndex: 'ram',
          width: '30%'
        }
      ],
      selectedRowKeys: [],
      oldZoneId: null,
      options: {
        page: 1,
        pageSize: 10,
        keyword: null
      }
    }
  },
  computed: {
    tableSource () {
      return this.computeItems.map((item) => {
        var maxCpuNumber = item.cpunumber
        var maxCpuSpeed = item.cpuspeed
        var maxMemory = item.memory
        var cpuNumberValue = (item.cpunumber !== null && item.cpunumber !== undefined && item.cpunumber > 0) ? item.cpunumber + '' : ''
        var cpuSpeedValue = (item.cpuspeed !== null && item.cpuspeed !== undefined && item.cpuspeed > 0) ? parseFloat(item.cpuspeed / 1000.0).toFixed(2) + '' : ''
        var ramValue = (item.memory !== null && item.memory !== undefined && item.memory > 0) ? item.memory + '' : ''
        if (item.iscustomized === true) {
          if ('serviceofferingdetails' in item &&
            'mincpunumber' in item.serviceofferingdetails &&
            'maxcpunumber' in item.serviceofferingdetails) {
            maxCpuNumber = item.serviceofferingdetails.maxcpunumber
            cpuNumberValue = item.serviceofferingdetails.mincpunumber + '-' + item.serviceofferingdetails.maxcpunumber
          }
          if ('serviceofferingdetails' in item &&
            'minmemory' in item.serviceofferingdetails &&
            'maxmemory' in item.serviceofferingdetails) {
            maxMemory = item.serviceofferingdetails.maxmemory
            ramValue = item.serviceofferingdetails.minmemory + '-' + item.serviceofferingdetails.maxmemory
          }
        }
        var disabled = false
        if (this.minimumCpunumber > 0 && ((item.iscustomized === false && maxCpuNumber !== this.minimumCpunumber) ||
            (item.iscustomized === true && maxCpuNumber < this.minimumCpunumber))) {
          disabled = true
        }
        if (disabled === false && this.minimumCpuspeed > 0 && maxCpuSpeed && maxCpuSpeed < this.minimumCpuspeed) {
          disabled = true
        }
        if (disabled === false && maxMemory && this.minimumMemory > 0 &&
          ((item.iscustomized === false && ((maxMemory < this.minimumMemory) || this.exactMatch && maxMemory !== this.minimumMemory)) ||
            (item.iscustomized === true && maxMemory < this.minimumMemory))) {
          disabled = true
        }
        if (this.selectedTemplate && this.selectedTemplate.hypervisor === 'VMware' && this.selectedTemplate.deployasis && item.rootdisksize) {
          disabled = true
        }
        if (this.autoscale && item.iscustomized) {
          disabled = true
        }
        if (this.allowAllOfferings) {
          disabled = false
        }
        return {
          key: item.id,
          name: item.name,
          cpu: cpuNumberValue.length > 0 ? `${cpuNumberValue} CPU x ${cpuSpeedValue} Ghz` : '',
          ram: ramValue.length > 0 ? `${ramValue} MB` : '',
          disabled: disabled,
          leaseduration: item.leaseduration
        }
      })
    },
    rowSelection () {
      return {
        type: 'radio',
        selectedRowKeys: this.selectedRowKeys || [],
        onChange: this.onSelectRow,
        getCheckboxProps: (record) => {
          return {
            disabled: record.disabled
          }
        }
      }
    }
  },
  watch: {
    value (newValue, oldValue) {
      if (newValue && newValue !== oldValue) {
        this.selectedRowKeys = [newValue]
      } else {
        this.selectedRowKeys = []
      }
    },
    loading () {
      if (!this.loading) {
        if (!this.preFillContent) {
          return
        }
        if (this.preFillContent.computeofferingid) {
          this.selectedRowKeys = [this.preFillContent.computeofferingid]
          this.$emit('select-compute-item', this.preFillContent.computeofferingid)
        } else {
          if (this.oldZoneId === this.zoneId) {
            return
          }
          this.oldZoneId = this.zoneId
          if (this.computeItems && this.computeItems.length > 0) {
            this.selectedRowKeys = [this.computeItems[0].id]
            this.$emit('select-compute-item', this.computeItems[0].id)
          }
        }
      }
    }
  },
  methods: {
    onSelectRow (value) {
      this.selectedRowKeys = value
      this.$emit('select-compute-item', value[0])
    },
    handleSearch (value) {
      this.filter = value
      this.options.page = 1
      this.options.pageSize = 10
      this.options.keyword = this.filter
      this.$emit('handle-search-filter', this.options)
    },
    onChangePage (page, pageSize) {
      this.options.page = page
      this.options.pageSize = pageSize
      this.$emit('handle-search-filter', this.options)
    },
    onChangePageSize (page, pageSize) {
      this.options.page = page
      this.options.pageSize = pageSize
      this.$emit('handle-search-filter', this.options)
    },
    onClickRow (record) {
      return {
        onClick: () => {
          if (record.disabled) {
            return
          }
          this.selectedRowKeys = [record.key]
          this.$emit('select-compute-item', record.key)
        }
      }
    },
    getRemainingLeaseText (leaseDuration) {
      if (leaseDuration > 0) {
        return leaseDuration + (leaseDuration === 1 ? ' day' : ' days')
      } else if (leaseDuration === 0) {
        return 'expiring today'
      } else {
        return 'over'
      }
    }
  }
}
</script>

<style lang="less" scoped>
  .ant-table-wrapper {
    margin: 2rem 0;
  }

  :deep(.ant-table-tbody) > tr > td {
    cursor: pointer;
  }
</style>
