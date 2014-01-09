/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.mapreduce.impl.task;

import com.hazelcast.mapreduce.JobPartitionState;
import com.hazelcast.mapreduce.JobProcessInformation;
import com.hazelcast.util.ValidationUtil;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class JobProcessInformationImpl implements JobProcessInformation {

    private final AtomicReferenceFieldUpdater<JobProcessInformationImpl, JobPartitionState[]> updater;

    private final JobSupervisor supervisor;

    private volatile JobPartitionState[] partitionStates;
    private AtomicInteger processedRecords = new AtomicInteger();

    public JobProcessInformationImpl(int partitionCount, JobSupervisor supervisor) {
        this.supervisor = supervisor;
        this.partitionStates = new JobPartitionState[partitionCount];
        this.updater = AtomicReferenceFieldUpdater.newUpdater(
                JobProcessInformationImpl.class, JobPartitionState[].class, "partitionStates");
    }

    @Override
    public JobPartitionState[] getPartitionStates() {
        return partitionStates;
    }

    @Override
    public int getProcessedRecords() {
        return processedRecords.get();
    }

    public void addProcessRecords(int records) {
        processedRecords.addAndGet(records);
    }

    public boolean updatePartitionState(int partitionId, JobPartitionState oldPartitionState,
                                        JobPartitionState newPartitionState) {

        ValidationUtil.isNotNull(newPartitionState, "newPartitionState");
        for (;; ) {
            JobPartitionState[] oldPartitionStates = getPartitionStates();
            if (oldPartitionStates[partitionId] != oldPartitionState) {
                return false;
            }

            JobPartitionState[] newPartitionStates = Arrays.copyOf(oldPartitionStates, oldPartitionStates.length);
            newPartitionStates[partitionId] = newPartitionState;
            if (updatePartitionState(oldPartitionStates, newPartitionStates)) {
                return true;
            }
        }
    }

    public boolean updatePartitionState(JobPartitionState[] oldPartitionStates,
                                        JobPartitionState[] newPartitionStates) {
        ValidationUtil.isNotNull(newPartitionStates, "newPartitionStates");
        if (oldPartitionStates.length != newPartitionStates.length) {
            throw new IllegalArgumentException("partitionStates need to have same length");
        }
        if (updater.compareAndSet(this, oldPartitionStates, newPartitionStates)) {
            supervisor.checkFullyProcessed(this);
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "JobProcessInformationImpl{" +
                "processedRecords=" + processedRecords +
                ", partitionStates=" + Arrays.toString(partitionStates) +
                '}';
    }

}
