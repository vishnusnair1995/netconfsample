/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.jaxrs;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;
import org.opendaylight.netconf.sal.rest.doc.api.ApiDocService;

public class ApiDocApplication extends Application {
    private final ApiDocService apiDocService;

    public ApiDocApplication(ApiDocService apiDocService) {
        this.apiDocService = apiDocService;
    }

    @Override
    public Set<Object> getSingletons() {
        Set<Object> singletons = new HashSet<>();
        singletons.add(apiDocService);
        singletons.add(new JaxbContextResolver());
        singletons.add(new JacksonJaxbJsonProvider());
        return singletons;
    }
}
