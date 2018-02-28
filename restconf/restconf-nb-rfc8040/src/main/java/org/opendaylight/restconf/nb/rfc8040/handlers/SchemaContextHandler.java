/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.handlers;

import com.google.common.base.Preconditions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.RestConnectorProvider;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.IetfYangLibrary;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.MonitoringModule;
import org.opendaylight.restconf.nb.rfc8040.utils.mapping.RestconfMappingNodeUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.tree.ConflictingModificationAppliedException;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link SchemaContextHandler}.
 *
 */
public class SchemaContextHandler implements SchemaContextListenerHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaContextHandler.class);

    @SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    private static volatile SchemaContext schemaContext;

    private final TransactionChainHandler transactionChainHandler;

    private final AtomicInteger moduleSetId = new AtomicInteger(0);

    /**
     * Constructor.
     *
     * @param transactionChainHandler Transaction chain handler
     */
    public SchemaContextHandler(final TransactionChainHandler transactionChainHandler) {
        this.transactionChainHandler = transactionChainHandler;
        schemaContext = null;
    }

    @Override
    @SuppressWarnings("checkstyle:hiddenField")
    public void onGlobalContextUpdated(final SchemaContext context) {
        Preconditions.checkNotNull(context);
        schemaContext = context;

        final Module ietfYangLibraryModule =
                context.findModule(IetfYangLibrary.MODULE_QNAME).orElse(null);
        NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>> normNode =
                RestconfMappingNodeUtil.mapModulesByIetfYangLibraryYang(context.getModules(), ietfYangLibraryModule,
                        context, String.valueOf(this.moduleSetId.incrementAndGet()));
        putData(normNode);

        final Module monitoringModule =
                schemaContext.findModule(MonitoringModule.MODULE_QNAME).orElse(null);
        normNode = RestconfMappingNodeUtil.mapCapabilites(monitoringModule);
        putData(normNode);
    }

    @Override
    public SchemaContext get() {
        return schemaContext;
    }

    public static SchemaContext getSchemaContext() {
        return schemaContext;
    }

    public static void setSchemaContext(final SchemaContext context) {
        schemaContext = context;
    }

    private void putData(
            final NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>> normNode) {
        final DOMDataWriteTransaction wTx = this.transactionChainHandler.get().newWriteOnlyTransaction();
        wTx.put(LogicalDatastoreType.OPERATIONAL,
                YangInstanceIdentifier.create(NodeIdentifier.create(normNode.getNodeType())), normNode);
        try {
            wTx.submit().checkedGet();
        } catch (final TransactionCommitFailedException e) {
            if (!(e.getCause() instanceof ConflictingModificationAppliedException)) {
                throw new RestconfDocumentedException("Problem occurred while putting data to DS.", e);
            }

            /*
              Ignore error when another cluster node is already putting the same data to DS.
              We expect that cluster is homogeneous and that node was going to write the same data
              (that means no retry is needed). Transaction chain reset must be invoked to be able
              to continue writing data with another transaction after failed transaction.
              This is workaround for bug:
              https://bugs.opendaylight.org/show_bug.cgi?id=7728
            */
            LOG.warn("Ignoring that another cluster node is already putting the same data to DS.", e);
            RestConnectorProvider.resetTransactionChainForAdapaters(this.transactionChainHandler.get());
        }
    }
}
