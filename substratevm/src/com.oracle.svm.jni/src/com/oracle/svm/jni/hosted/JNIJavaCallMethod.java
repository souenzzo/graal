/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.jni.hosted;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.word.WordTypes;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.NonBytecodeStaticMethod;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/** FIXME */
@SuppressWarnings("unused")
public class JNIJavaCallMethod extends NonBytecodeStaticMethod {

    public static class Factory {
        public JNIJavaCallMethod create(ResolvedJavaMethod method, AnalysisUniverse analysisUniverse, WordTypes wordTypes) {
            throw VMError.unimplemented();
        }
    }

    protected JNIJavaCallMethod(ResolvedJavaMethod targetMethod, AnalysisUniverse analysisUniverse, WordTypes wordTypes) {
        super(null, null, null, null);
        throw VMError.unimplemented();
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        throw VMError.unimplemented();
    }

    protected ValueNode createMethodCall(JNIGraphKit kit, ResolvedJavaMethod invokeMethod, CallTargetNode.InvokeKind invokeKind, FrameStateBuilder frameState, ValueNode... args) {
        throw VMError.unimplemented();
    }
}
