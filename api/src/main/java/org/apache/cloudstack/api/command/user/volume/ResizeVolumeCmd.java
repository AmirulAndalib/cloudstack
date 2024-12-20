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
package org.apache.cloudstack.api.command.user.volume;
import org.apache.cloudstack.api.BaseAsyncCmd;

import org.apache.cloudstack.acl.SecurityChecker.AccessType;
import org.apache.cloudstack.api.ACL;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiCommandResourceType;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ResponseObject.ResponseView;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.command.user.UserCmd;
import org.apache.cloudstack.api.response.DiskOfferingResponse;
import org.apache.cloudstack.api.response.VolumeResponse;
import org.apache.cloudstack.context.CallContext;

import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.projects.Project;
import com.cloud.storage.Volume;
import com.cloud.user.Account;


@APICommand(name = "resizeVolume", description = "Resizes a volume", responseObject = VolumeResponse.class, responseView = ResponseView.Restricted, entityType = {Volume.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ResizeVolumeCmd extends BaseAsyncCmd implements UserCmd {

    private static final String s_name = "resizevolumeresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @ACL(accessType = AccessType.OperateEntry)
    @Parameter(name = ApiConstants.ID, entityType = VolumeResponse.class, required = true, type = CommandType.UUID, description = "the ID of the disk volume")
    private Long id;

    @Parameter(name = ApiConstants.MIN_IOPS, type = CommandType.LONG, required = false, description = "New minimum number of IOPS")
    private Long minIops;

    @Parameter(name = ApiConstants.MAX_IOPS, type = CommandType.LONG, required = false, description = "New maximum number of IOPS")
    private Long maxIops;

    @Parameter(name = ApiConstants.SIZE, type = CommandType.LONG, required = false, description = "New volume size in GB")
    private Long size;

    @Parameter(name = ApiConstants.SHRINK_OK, type = CommandType.BOOLEAN, required = false, description = "Verify OK to Shrink")
    private boolean shrinkOk;

    @Parameter(name = ApiConstants.DISK_OFFERING_ID,
               entityType = DiskOfferingResponse.class,
               type = CommandType.UUID,
               required = false,
               description = "new disk offering id")
    private Long newDiskOfferingId;

    @Parameter(name = ApiConstants.AUTO_MIGRATE, type = CommandType.BOOLEAN, required = false,
            description = "Flag to allow automatic migration of the volume to another suitable storage pool that accommodates the new size", since = "4.20.1")
    private Boolean autoMigrate;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public ResizeVolumeCmd() {}

    public ResizeVolumeCmd(Long id, Long minIops, Long maxIops) {
        this.id = id;
        this.minIops = minIops;
        this.maxIops = maxIops;
    }

    public ResizeVolumeCmd(Long id, Long minIops, Long maxIops, long diskOfferingId) {
        this.id = id;
        this.minIops = minIops;
        this.maxIops = maxIops;
        this.newDiskOfferingId = diskOfferingId;
    }

    //TODO use the method getId() instead of this one.
    public Long getEntityId() {
        return id;
    }

    public Long getId() {
        return getEntityId();
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMinIops() {
        return minIops;
    }

    public Long getMaxIops() {
        return maxIops;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public boolean isShrinkOk() {
        return shrinkOk;
    }

    public Long getNewDiskOfferingId() {
        return newDiskOfferingId;
    }

    public boolean getAutoMigrate() {
        return autoMigrate == null ? false : autoMigrate;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public ApiCommandResourceType getApiResourceType() {
        return ApiCommandResourceType.Volume;
    }

    @Override
    public Long getApiResourceId() {
        return id;
    }

    public static String getResultObjectName() {
        return "volume";
    }

   @Override
    public long getEntityOwnerId() {

        Volume volume = _entityMgr.findById(Volume.class, getEntityId());
        if (volume == null) {
                throw new InvalidParameterValueException("Unable to find volume by id=" + id);
        }

        Account account = _accountService.getAccount(volume.getAccountId());
        //Can resize volumes for enabled projects/accounts only
        if (account.getType() == Account.Type.PROJECT) {
                Project project = _projectService.findByProjectAccountId(volume.getAccountId());
            if (project.getState() != Project.State.Active) {
                throw new PermissionDeniedException("Can't add resources to  project id=" + project.getId() + " in state=" + project.getState() +
                    " as it's no longer active");
            }
        } else if (account.getState() == Account.State.DISABLED) {
            throw new PermissionDeniedException("The owner of volume " + id + "  is disabled: " + account);
        }

        return volume.getAccountId();
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_VOLUME_RESIZE;
    }

    @Override
    public String getEventDescription() {
        if (getSize() != null) {
            return "Volume Id: " + this._uuidMgr.getUuid(Volume.class, getEntityId()) + " to size " + getSize() + " GB";
        } else {
            return "Volume Id: " + this._uuidMgr.getUuid(Volume.class, getEntityId());
        }
    }

    @Override
    public void execute() {
        Volume volume = null;
        try {
            if (size != null) {
                CallContext.current().setEventDetails("Volume Id: " + this._uuidMgr.getUuid(Volume.class, getEntityId()) + " to size " + getSize() + " GB");
            } else {
                CallContext.current().setEventDetails("Volume Id: " + this._uuidMgr.getUuid(Volume.class, getEntityId()));
            }

            volume = _volumeService.resizeVolume(this);
        } catch (ResourceAllocationException ex) {
            logger.error(ex.getMessage());
            throw new ServerApiException(ApiErrorCode.RESOURCE_ALLOCATION_ERROR, ex.getMessage());
        } catch (InvalidParameterValueException ex) {
            logger.info(ex.getMessage());
            throw new ServerApiException(ApiErrorCode.UNSUPPORTED_ACTION_ERROR, ex.getMessage());
        }

        if (volume != null) {
            VolumeResponse response = _responseGenerator.createVolumeResponse(getResponseView(), volume);
            //FIXME - have to be moved to ApiResponseHelper
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to resize volume");
        }
    }
}
