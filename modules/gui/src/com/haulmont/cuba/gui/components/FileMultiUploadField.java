/*
 * Copyright (c) 2008-2016 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.haulmont.cuba.gui.components;

import java.util.Map;
import java.util.UUID;

public interface FileMultiUploadField extends UploadField {

    String NAME = "multiUpload";

    /**
     * Get uploads map
     *
     * @return Map ( UUID - Id of file in FileUploadingAPI, String - FileName )
     */
    Map<UUID, String> getUploadsMap();

    /**
     * Clear uploads list
     */
    void clearUploads();

    @FunctionalInterface
    interface QueueUploadCompleteListener {
        void queueUploadComplete();
    }

    void addQueueUploadCompleteListener(QueueUploadCompleteListener listener);
    void removeQueueUploadCompleteListener(QueueUploadCompleteListener listener);
}