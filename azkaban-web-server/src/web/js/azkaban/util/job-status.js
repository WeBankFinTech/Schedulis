/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

var statusList = ["FAILED", "FAILED_FINISHING", "SUCCEEDED", "RUNNING",
  "WAITING", "KILLED", "DISABLED", "READY", "CANCELLED", "UNKNOWN", "PAUSED",
  "SKIPPED", "QUEUED", "FAILED_SUCCEEDED", "FAILED_WAITING", "FAILED_SKIPPED",
  "FAILED_RETRYING", "RETRIED_SUCCEEDED", "SYSTEM_PAUSED", "FAILED_SKIPPED_DISABLED"];
var statusStringMap = {
  "QUEUED": "Queued",
  "SKIPPED": "Skipped",
  "PREPARING": "Preparing",
  "FAILED": "Failed",
  "SUCCEEDED": "Success",
  "FAILED_FINISHING": "Running w/Failure",
  "RUNNING": "Running",
  "WAITING": "Waiting",
  "KILLING": "Killing",
  "KILLED": "Killed",
  "CANCELLED": "Cancelled",
  "DISABLED": "Disabled",
  "READY": "Ready",
  "UNKNOWN": "Unknown",
  "PAUSED": "Paused",
  "FAILED_SUCCEEDED": "Failed, treated as success",
  "FAILED_WAITING": "Failed waiting",
  "FAILED_SKIPPED": "Failed skipped",
  "FAILED_RETRYING": "Failed retrying",
  "RETRIED_SUCCEEDED": "Retried succeeded",
  "SYSTEM_PAUSED": "System paused",
  "FAILED_SKIPPED_DISABLED": "Failed skipped disabled",
  "PRE_FAILED": "Pre failed",
};
