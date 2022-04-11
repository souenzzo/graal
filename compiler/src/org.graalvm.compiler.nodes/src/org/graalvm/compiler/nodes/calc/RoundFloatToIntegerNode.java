/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.nodes.calc;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_32;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_32;

import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Round floating-point value to integer value. Intrinsic for {@link Math#round}.
 */
@NodeInfo(cycles = CYCLES_32, size = SIZE_32)
public final class RoundFloatToIntegerNode extends UnaryNode implements ArithmeticLIRLowerable {

    public static final NodeClass<RoundFloatToIntegerNode> TYPE = NodeClass.create(RoundFloatToIntegerNode.class);

    public RoundFloatToIntegerNode(ValueNode value) {
        super(TYPE, roundStamp(((FloatStamp) value.stamp(NodeView.DEFAULT))), value);
    }

    private static IntegerStamp roundStamp(FloatStamp stamp) {
        double min = stamp.lowerBound();
        double max = stamp.upperBound();

        if (stamp.getBits() == 32) {
            long lowerBound = Math.round((float) min);
            long upperBound = Math.round((float) max);
            IntegerStamp newStamp = StampFactory.forInteger(32, lowerBound, upperBound);
            if (stamp.canBeNaN()) {
                return (IntegerStamp) newStamp.meet(StampFactory.forInteger(32, 0, 0));
            } else {
                return newStamp;
            }
        } else {
            assert stamp.getBits() == 64;
            long lowerBound = Math.round(min);
            long upperBound = Math.round(max);
            IntegerStamp newStamp = StampFactory.forInteger(64, lowerBound, upperBound);
            if (stamp.canBeNaN()) {
                return (IntegerStamp) newStamp.meet(StampFactory.forInteger(64, 0, 0));
            } else {
                return newStamp;
            }
        }
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        assert newStamp.isCompatible(getValue().stamp(NodeView.DEFAULT));
        return roundStamp((FloatStamp) newStamp);
    }

    private static ValueNode tryFold(ValueNode input) {
        if (input.isConstant()) {
            JavaConstant c = input.asJavaConstant();
            if (c.getJavaKind() == JavaKind.Double) {
                return ConstantNode.forLong(Math.round(c.asDouble()));
            } else if (c.getJavaKind() == JavaKind.Float) {
                return ConstantNode.forInt(Math.round(c.asFloat()));
            }
        }
        return null;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode folded = tryFold(forValue);
        return folded != null ? folded : this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        builder.setResult(this, gen.emitRoundFloatToInteger(builder.operand(getValue())));
    }
}
