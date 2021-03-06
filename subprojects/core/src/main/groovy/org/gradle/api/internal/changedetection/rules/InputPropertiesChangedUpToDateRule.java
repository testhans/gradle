/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.changedetection.rules;

import org.gradle.api.Action;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskExecution;
import org.gradle.api.internal.changedetection.TaskUpToDateState;
import org.gradle.api.internal.changedetection.TaskUpToDateStateChange;
import org.gradle.util.ChangeListener;
import org.gradle.util.DiffUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * A rule which detects changes in the input properties of a task.
 */
public class InputPropertiesChangedUpToDateRule {
    public static TaskUpToDateState create(final TaskInternal task, final TaskExecution previousExecution, final TaskExecution currentExecution) {
        final Map<String, Object> properties = new HashMap<String, Object>(task.getInputs().getProperties());
        currentExecution.setInputProperties(properties);

        return new TaskUpToDateState() {
            public void findChanges(final Action<? super TaskUpToDateStateChange> failures) {
                DiffUtil.diff(properties, previousExecution.getInputProperties(), new ChangeListener<Map.Entry<String, Object>>() {
                    public void added(Map.Entry<String, Object> element) {
                        failures.execute(new DescriptiveChange("Input property '%s' has been added for %s", element.getKey(), task));
                    }

                    public void removed(Map.Entry<String, Object> element) {
                        failures.execute(new DescriptiveChange("Input property '%s' has been removed for %s", element.getKey(), task));
                    }

                    public void changed(Map.Entry<String, Object> element) {
                        failures.execute(new DescriptiveChange("Value of input property '%s' has changed for %s", element.getKey(), task));
                    }
                });
            }

            public void snapshotAfterTask() {
            }
        };
    }
}
