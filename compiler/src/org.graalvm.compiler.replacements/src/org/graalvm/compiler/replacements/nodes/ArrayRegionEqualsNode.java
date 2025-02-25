/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.core.common.GraalOptions.UseGraalStubs;

import java.util.EnumSet;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.StrideUtil;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.util.ConstantReflectionUtil;
import org.graalvm.compiler.replacements.NodeStrideUtil;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.Value;

// JaCoCo Exclude

/**
 * Compares two array regions with a given length. This node can compare regions of arrays of the
 * same primitive element kinds. As a special case, it also supports comparing an array region
 * interpreted as {@code char}s with an array region interpreted as {@code byte}s, in which case the
 * {@code byte} values are zero-extended for the comparison. In this case, the first kind must be
 * {@code char}, and the underlying array must be a {@code byte} array (this condition is not
 * checked). Other combinations of kinds are currently not allowed.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_16)
public class ArrayRegionEqualsNode extends PureFunctionStubIntrinsicNode implements Canonicalizable, LIRLowerable, ConstantReflectionUtil.ArrayBaseOffsetProvider {

    public static final NodeClass<ArrayRegionEqualsNode> TYPE = NodeClass.create(ArrayRegionEqualsNode.class);

    /** {@link JavaKind} of the arrays to compare. */
    protected final JavaKind strideA;
    protected final JavaKind strideB;

    /**
     * Pointer to the first array object.
     */
    @Input protected ValueNode arrayA;

    /**
     * Byte offset to be added to the first array pointer. Must include the array's base offset!
     */
    @Input protected ValueNode offsetA;

    /**
     * Pointer to the second array object.
     */
    @Input protected ValueNode arrayB;

    /**
     * Byte offset to be added to the second array pointer. Must include the array's base offset!
     */
    @Input protected ValueNode offsetB;

    /**
     * Length of the array region.
     */
    @Input protected ValueNode length;

    /**
     * Optional argument for dispatching to any combination of strides at runtime, as described in
     * {@link org.graalvm.compiler.core.common.StrideUtil}.
     */
    @OptionalInput protected ValueNode dynamicStrides;

    public ArrayRegionEqualsNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length, ValueNode dynamicStrides, LocationIdentity locationIdentity) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, dynamicStrides, null, null, null, locationIdentity);
    }

    public ArrayRegionEqualsNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length,
                    @ConstantNodeParameter JavaKind strideA,
                    @ConstantNodeParameter JavaKind strideB) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, null, strideA, strideB, null, strideA != strideB ? LocationIdentity.ANY_LOCATION : NamedLocationIdentity.getArrayLocation(strideA));
    }

    public ArrayRegionEqualsNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length,
                    @ConstantNodeParameter JavaKind strideA,
                    @ConstantNodeParameter JavaKind strideB,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, null, strideA, strideB, runtimeCheckedCPUFeatures,
                        strideA != strideB ? LocationIdentity.ANY_LOCATION : NamedLocationIdentity.getArrayLocation(strideA));
    }

    public ArrayRegionEqualsNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length,
                    @ConstantNodeParameter JavaKind strideA,
                    @ConstantNodeParameter JavaKind strideB,
                    LocationIdentity locationIdentity) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, null, strideA, strideB, null, locationIdentity);
    }

    public ArrayRegionEqualsNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length, ValueNode dynamicStrides) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, dynamicStrides, null, null, null, LocationIdentity.ANY_LOCATION);
    }

    public ArrayRegionEqualsNode(ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length, ValueNode dynamicStrides,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(TYPE, arrayA, offsetA, arrayB, offsetB, length, dynamicStrides, null, null, runtimeCheckedCPUFeatures, LocationIdentity.ANY_LOCATION);
    }

    protected ArrayRegionEqualsNode(NodeClass<? extends ArrayRegionEqualsNode> c, ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length, ValueNode dynamicStrides,
                    JavaKind strideA,
                    JavaKind strideB,
                    EnumSet<?> runtimeCheckedCPUFeatures,
                    LocationIdentity locationIdentity) {
        super(c, StampFactory.forKind(JavaKind.Boolean), runtimeCheckedCPUFeatures, locationIdentity);
        this.strideA = strideA;
        this.strideB = strideB;
        this.arrayA = arrayA;
        this.offsetA = offsetA;
        this.arrayB = arrayB;
        this.offsetB = offsetB;
        this.length = length;
        this.dynamicStrides = dynamicStrides;
        assert strideA == null || strideA.isPrimitive() && strideB.isPrimitive() : "expected primitive kinds, got: " + strideA + ", " + strideB;
    }

    public static boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, int length, @ConstantNodeParameter JavaKind kind) {
        return regionEquals(arrayA, offsetA, arrayB, offsetB, length, kind, kind);
    }

    @NodeIntrinsic
    public static native boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, int length, @ConstantNodeParameter JavaKind kind1, @ConstantNodeParameter JavaKind kind2);

    @NodeIntrinsic
    public static native boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, int length, @ConstantNodeParameter JavaKind kind1, @ConstantNodeParameter JavaKind kind2,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @NodeIntrinsic
    public static native boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, int length, int stride);

    @NodeIntrinsic
    public static native boolean regionEquals(Object arrayA, long offsetA, Object arrayB, long offsetB, int length, int stride,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    public ValueNode getArrayA() {
        return arrayA;
    }

    public ValueNode getOffsetA() {
        return offsetA;
    }

    public ValueNode getArrayB() {
        return arrayB;
    }

    public ValueNode getOffsetB() {
        return offsetB;
    }

    public JavaKind getStrideA() {
        return strideA;
    }

    public JavaKind getStrideB() {
        return strideB;
    }

    public ValueNode getLength() {
        return length;
    }

    public ValueNode getDynamicStrides() {
        return dynamicStrides;
    }

    public int getDirectStubCallIndex() {
        return NodeStrideUtil.getDirectStubCallIndex(dynamicStrides, strideA, strideB);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        if (UseGraalStubs.getValue(graph().getOptions())) {
            ForeignCallLinkage linkage = gen.lookupGraalStub(this);
            if (linkage != null) {
                final Value result;
                if (getDirectStubCallIndex() < 0) {
                    result = gen.getLIRGeneratorTool().emitForeignCall(linkage, null, gen.operand(arrayA), gen.operand(offsetA), gen.operand(arrayB), gen.operand(offsetB), gen.operand(length),
                                    gen.operand(dynamicStrides));
                } else {
                    result = gen.getLIRGeneratorTool().emitForeignCall(linkage, null, gen.operand(arrayA), gen.operand(offsetA), gen.operand(arrayB), gen.operand(offsetB), gen.operand(length));
                }
                gen.setResult(this, result);
                return;
            }
        }
        generateArrayRegionEquals(gen);
    }

    @Override
    public int getArrayBaseOffset(MetaAccessProvider metaAccess, @SuppressWarnings("unused") ValueNode array, JavaKind elementKind) {
        return metaAccess.getArrayBaseOffset(elementKind);
    }

    protected void generateArrayRegionEquals(NodeLIRBuilderTool gen) {
        final Value result;
        if (strideA != null) {
            if (strideA == strideB) {
                result = gen.getLIRGeneratorTool().emitArrayEquals(strideA,
                                0, 0, getRuntimeCheckedCPUFeatures(), gen.operand(arrayA), gen.operand(offsetA), gen.operand(arrayB), gen.operand(offsetB), gen.operand(length));
            } else {
                result = gen.getLIRGeneratorTool().emitArrayEquals(strideA, strideB,
                                0, 0, getRuntimeCheckedCPUFeatures(), gen.operand(arrayA), gen.operand(offsetA), gen.operand(arrayB), gen.operand(offsetB), gen.operand(length));
            }
        } else {
            int directStubCallIndex = getDirectStubCallIndex();
            if (directStubCallIndex < 0) {
                result = gen.getLIRGeneratorTool().emitArrayEquals(
                                0, 0, getRuntimeCheckedCPUFeatures(), gen.operand(arrayA), gen.operand(offsetA), gen.operand(arrayB), gen.operand(offsetB), gen.operand(length),
                                gen.operand(dynamicStrides));
            } else {
                result = gen.getLIRGeneratorTool().emitArrayEquals(
                                StrideUtil.getConstantStrideA(directStubCallIndex),
                                StrideUtil.getConstantStrideB(directStubCallIndex),
                                0, 0, getRuntimeCheckedCPUFeatures(), gen.operand(arrayA), gen.operand(offsetA), gen.operand(arrayB), gen.operand(offsetB), gen.operand(length));
            }
        }
        gen.setResult(this, result);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        if ((dynamicStrides == null || dynamicStrides.isJavaConstant()) && length.isJavaConstant()) {
            int len = length.asJavaConstant().asInt();
            JavaKind constStrideA = NodeStrideUtil.getConstantStrideA(dynamicStrides, strideA);
            JavaKind constStrideB = NodeStrideUtil.getConstantStrideB(dynamicStrides, strideB);
            if (len * Math.max(constStrideA.getByteCount(), constStrideB.getByteCount()) < GraalOptions.ArrayRegionEqualsConstantLimit.getValue(tool.getOptions()) &&
                            ConstantReflectionUtil.canFoldReads(tool, arrayA, offsetA, constStrideA, len, this) &&
                            ConstantReflectionUtil.canFoldReads(tool, arrayB, offsetB, constStrideB, len, this)) {
                Integer startIndex1 = ConstantReflectionUtil.startIndex(tool, arrayA, offsetA.asJavaConstant(), constStrideA, this);
                Integer startIndex2 = ConstantReflectionUtil.startIndex(tool, arrayB, offsetB.asJavaConstant(), constStrideB, this);
                return ConstantNode.forBoolean(arrayRegionEquals(tool, arrayA, constStrideA, startIndex1, arrayB, constStrideB, startIndex2, len));
            }
        }
        return this;
    }

    private static boolean arrayRegionEquals(CanonicalizerTool tool, ValueNode a, JavaKind constStrideA, int startIndexA, ValueNode b, JavaKind constStrideB, int startIndexB, int len) {
        JavaKind arrayKindA = a.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        JavaKind arrayKindB = b.stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess()).getComponentType().getJavaKind();
        ConstantReflectionProvider constantReflection = tool.getConstantReflection();
        for (int i = 0; i < len; i++) {
            int valueA = ConstantReflectionUtil.readTypePunned(constantReflection, a.asJavaConstant(), arrayKindA, constStrideA, startIndexA + i);
            int valueB = ConstantReflectionUtil.readTypePunned(constantReflection, b.asJavaConstant(), arrayKindB, constStrideB, startIndexB + i);
            if (valueA != valueB) {
                return false;
            }
        }
        return true;
    }
}
