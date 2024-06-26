/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.queryengine.plan.expression.unary;

import org.apache.iotdb.db.queryengine.execution.MemoryEstimationHelper;
import org.apache.iotdb.db.queryengine.plan.expression.Expression;
import org.apache.iotdb.db.queryengine.plan.expression.ExpressionType;
import org.apache.iotdb.db.queryengine.plan.expression.leaf.ConstantOperand;
import org.apache.iotdb.db.queryengine.plan.expression.leaf.LeafOperand;
import org.apache.iotdb.db.queryengine.plan.expression.leaf.TimeSeriesOperand;
import org.apache.iotdb.db.queryengine.plan.expression.multi.FunctionExpression;
import org.apache.iotdb.db.queryengine.plan.expression.visitor.ExpressionVisitor;

import org.apache.tsfile.utils.RamUsageEstimator;
import org.apache.tsfile.utils.ReadWriteIOUtils;

import javax.validation.constraints.NotNull;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedHashSet;

public class InExpression extends UnaryExpression {
  private static final long INSTANCE_SIZE =
      RamUsageEstimator.shallowSizeOfInstance(InExpression.class);
  private final boolean isNotIn;
  private final LinkedHashSet<String> values;

  public InExpression(Expression expression, boolean isNotIn, LinkedHashSet<String> values) {
    super(expression);
    this.isNotIn = isNotIn;
    this.values = values;
  }

  public InExpression(ByteBuffer byteBuffer) {
    super(Expression.deserialize(byteBuffer));
    isNotIn = ReadWriteIOUtils.readBool(byteBuffer);
    final int size = ReadWriteIOUtils.readInt(byteBuffer);
    values = new LinkedHashSet<>();
    for (int i = 0; i < size; ++i) {
      values.add(ReadWriteIOUtils.readString(byteBuffer));
    }
  }

  public boolean isNotIn() {
    return isNotIn;
  }

  public LinkedHashSet<String> getValues() {
    return values;
  }

  @Override
  protected String getExpressionStringInternal() {
    String operator = isNotIn ? " NOT IN (" : " IN (";
    StringBuilder stringBuilder = new StringBuilder();
    if (expression instanceof FunctionExpression || expression instanceof LeafOperand) {
      stringBuilder.append(expression.getExpressionString()).append(operator);
    } else {
      stringBuilder
          .append('(')
          .append(expression.getExpressionString())
          .append(')')
          .append(operator);
    }
    return appendValuesToBuild(stringBuilder).toString();
  }

  @NotNull
  private StringBuilder appendValuesToBuild(StringBuilder stringBuilder) {
    Iterator<String> iterator = values.iterator();
    if (iterator.hasNext()) {
      stringBuilder.append(iterator.next());
    }
    while (iterator.hasNext()) {
      stringBuilder.append(',').append(iterator.next());
    }
    stringBuilder.append(')');
    return stringBuilder;
  }

  @Override
  public ExpressionType getExpressionType() {
    return ExpressionType.IN;
  }

  @Override
  protected void serialize(ByteBuffer byteBuffer) {
    super.serialize(byteBuffer);
    ReadWriteIOUtils.write(isNotIn, byteBuffer);
    ReadWriteIOUtils.write(values.size(), byteBuffer);
    for (String value : values) {
      ReadWriteIOUtils.write(value, byteBuffer);
    }
  }

  @Override
  protected void serialize(DataOutputStream stream) throws IOException {
    super.serialize(stream);
    ReadWriteIOUtils.write(isNotIn, stream);
    ReadWriteIOUtils.write(values.size(), stream);
    for (String value : values) {
      ReadWriteIOUtils.write(value, stream);
    }
  }

  @Override
  public String getOutputSymbolInternal() {
    String operator = isNotIn ? " NOT IN (" : " IN (";
    StringBuilder stringBuilder = new StringBuilder();
    if (expression instanceof FunctionExpression
        || expression instanceof ConstantOperand
        || expression instanceof TimeSeriesOperand) {
      stringBuilder.append(expression.getOutputSymbol()).append(operator);
    } else {
      stringBuilder.append('(').append(expression.getOutputSymbol()).append(')').append(operator);
    }
    return appendValuesToBuild(stringBuilder).toString();
  }

  @Override
  public <R, C> R accept(ExpressionVisitor<R, C> visitor, C context) {
    return visitor.visitInExpression(this, context);
  }

  @Override
  public long ramBytesUsed() {
    return INSTANCE_SIZE
        + MemoryEstimationHelper.getEstimatedSizeOfAccountableObject(expression)
        + (values == null ? 0 : values.stream().mapToLong(RamUsageEstimator::sizeOf).sum());
  }
}
