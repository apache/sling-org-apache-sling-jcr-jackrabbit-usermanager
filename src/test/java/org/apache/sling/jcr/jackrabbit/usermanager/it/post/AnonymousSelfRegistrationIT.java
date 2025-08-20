/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.jcr.jackrabbit.usermanager.it.post;

import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Tests for the 'createUser' Sling Post Operation
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class AnonymousSelfRegistrationIT extends UserManagerClientTestSupport {

    @Override
    protected Option[] additionalOptions() {
        // supply additional configuration that enables anonymous self registration
        return composite(super.additionalOptions())
                .add(newConfiguration("org.apache.sling.jackrabbit.usermanager.impl.post.CreateUserServlet")
                        .put("self.registration.enabled", true).asOption())
                .getOptions();
    }

    @Test
    public void testAnonymousSelfRegistration() throws IOException {
        String postUrl = String.format("%s/system/userManager/user.create.html", baseServerUri);

        String userId = "testUser" + getNextInt();
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair(":name", userId));
        postParams.add(new BasicNameValuePair("pwd", "testPwd"));
        postParams.add(new BasicNameValuePair("pwdConfirm", "testPwd"));
        //user create without logging in as a privileged user should return ok
        assertAuthenticatedPostStatus(null, postUrl, HttpServletResponse.SC_OK, postParams, null);
    }

}
