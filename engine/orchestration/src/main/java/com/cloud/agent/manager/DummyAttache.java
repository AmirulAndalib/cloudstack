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
package com.cloud.agent.manager;

import com.cloud.agent.transport.Request;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.host.Status;
import com.cloud.hypervisor.Hypervisor;

public class DummyAttache extends AgentAttache {

    public DummyAttache(AgentManagerImpl agentMgr, long id, String uuid, String name, final Hypervisor.HypervisorType hypervisorType, boolean maintenance) {
        super(agentMgr, id, uuid, name, hypervisorType, maintenance);
    }

    @Override
    public void disconnect(Status state) {

    }

    @Override
    protected boolean isClosed() {
        return false;
    }

    @Override
    public void send(Request req) throws AgentUnavailableException {

    }

}
