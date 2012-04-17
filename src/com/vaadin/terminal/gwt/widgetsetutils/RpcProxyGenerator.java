/*
@VaadinApache2LicenseForJavaFiles@
 */

package com.vaadin.terminal.gwt.widgetsetutils;

import java.io.PrintWriter;

import com.google.gwt.core.ext.Generator;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.TreeLogger.Type;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JMethod;
import com.google.gwt.core.ext.typeinfo.JParameter;
import com.google.gwt.core.ext.typeinfo.TypeOracle;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;
import com.vaadin.terminal.gwt.client.ApplicationConnection;
import com.vaadin.terminal.gwt.client.ServerConnector;
import com.vaadin.terminal.gwt.client.communication.InitializableServerRpc;
import com.vaadin.terminal.gwt.client.communication.MethodInvocation;
import com.vaadin.terminal.gwt.client.communication.ServerRpc;

/**
 * GWT generator that creates client side proxy classes for making RPC calls
 * from the client to the server.
 * 
 * GWT.create() calls for interfaces extending {@link ServerRpc} are affected,
 * and a proxy implementation is created. Note that the init(...) method of the
 * proxy must be called before the proxy is used.
 * 
 * @since 7.0
 */
public class RpcProxyGenerator extends Generator {
    @Override
    public String generate(TreeLogger logger, GeneratorContext ctx,
            String requestedClassName) throws UnableToCompleteException {
        logger.log(TreeLogger.DEBUG, "Running RpcProxyGenerator", null);

        TypeOracle typeOracle = ctx.getTypeOracle();
        assert (typeOracle != null);

        JClassType requestedType = typeOracle.findType(requestedClassName);
        if (requestedType == null) {
            logger.log(TreeLogger.ERROR, "Unable to find metadata for type '"
                    + requestedClassName + "'", null);
            throw new UnableToCompleteException();
        }

        String generatedClassName = "ServerRpc_"
                + requestedType.getName().replaceAll("[$.]", "_");

        JClassType initializableInterface = typeOracle
                .findType(InitializableServerRpc.class.getCanonicalName());

        ClassSourceFileComposerFactory composer = new ClassSourceFileComposerFactory(
                requestedType.getPackage().getName(), generatedClassName);
        composer.addImplementedInterface(requestedType.getQualifiedSourceName());
        composer.addImplementedInterface(initializableInterface
                .getQualifiedSourceName());
        composer.addImport(MethodInvocation.class.getCanonicalName());

        PrintWriter printWriter = ctx.tryCreate(logger,
                composer.getCreatedPackage(),
                composer.getCreatedClassShortName());
        if (printWriter != null) {
            logger.log(Type.INFO, "Generating client proxy for RPC interface '"
                    + requestedType.getQualifiedSourceName() + "'");
            SourceWriter writer = composer.createSourceWriter(ctx, printWriter);

            // constructor
            writer.println("public " + generatedClassName + "() {}");

            // initialization etc.
            writeCommonFieldsAndMethods(logger, writer, typeOracle);

            // actual proxy methods forwarding calls to the server
            writeRemoteProxyMethods(logger, writer, typeOracle, requestedType,
                    requestedType.isClassOrInterface().getInheritableMethods());

            // End of class
            writer.outdent();
            writer.println("}");

            ctx.commit(logger, printWriter);
        }

        return composer.getCreatedClassName();
    }

    private void writeCommonFieldsAndMethods(TreeLogger logger,
            SourceWriter writer, TypeOracle typeOracle) {
        JClassType applicationConnectionClass = typeOracle
                .findType(ApplicationConnection.class.getCanonicalName());

        // fields
        writer.println("private " + ServerConnector.class.getName()
                + " connector;");

        // init method from the RPC interface
        writer.println("public void initRpc(" + ServerConnector.class.getName()
                + " connector) {");
        writer.indent();
        writer.println("this.connector = connector;");
        writer.outdent();
        writer.println("}");
    }

    private static void writeRemoteProxyMethods(TreeLogger logger,
            SourceWriter writer, TypeOracle typeOracle,
            JClassType requestedType, JMethod[] methods) {
        for (JMethod m : methods) {
            writer.print(m.getReadableDeclaration(false, false, false, false,
                    true));
            writer.println(" {");
            writer.indent();

            writer.print("connector.getConnection().addMethodInvocationToQueue(new MethodInvocation(connector.getConnectorId(), \""
                    + requestedType.getQualifiedBinaryName() + "\", \"");
            writer.print(m.getName());
            writer.print("\", new Object[] {");
            // new Object[] { ... } for parameters - autoboxing etc. by the
            // compiler
            JParameter[] parameters = m.getParameters();
            boolean first = true;
            for (JParameter p : parameters) {
                if (!first) {
                    writer.print(", ");
                }
                first = false;

                writer.print(p.getName());
            }
            writer.println("}), true);");

            writer.outdent();
            writer.println("}");
        }
    }
}
