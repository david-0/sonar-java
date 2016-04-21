/*
 * SonarQube Java
 * Copyright (C) 2012-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.se.symbolicvalues;

import com.google.common.collect.ImmutableList;

import org.sonar.java.se.ProgramState;
import org.sonar.java.se.checks.UnclosedResourcesCheck;
import org.sonar.java.se.constraint.BooleanConstraint;
import org.sonar.java.se.constraint.Constraint;
import org.sonar.java.se.constraint.ObjectConstraint;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;

import javax.annotation.CheckForNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RelationalSymbolicValue extends BinarySymbolicValue {

  public enum Kind {
    EQUAL("=="),
    NOT_EQUAL("!="),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    METHOD_EQUALS(".EQ."),
    NOT_METHOD_EQUALS(".NE.");

    final String operand;

    Kind(String operand) {
      this.operand = operand;
    }

    public Kind inverse() {
      switch (this) {
        case GREATER_THAN:
          return LESS_THAN_OR_EQUAL;
        case GREATER_THAN_OR_EQUAL:
          return LESS_THAN;
        case LESS_THAN:
          return GREATER_THAN_OR_EQUAL;
        case LESS_THAN_OR_EQUAL:
          return GREATER_THAN;
        case METHOD_EQUALS:
          return NOT_METHOD_EQUALS;
        case NOT_METHOD_EQUALS:
          return METHOD_EQUALS;
        default:
          return this;
      }
    }

    public Kind symmetric() {
      Kind sym;
      switch (this) {
        case GREATER_THAN:
          sym = LESS_THAN;
          break;
        case GREATER_THAN_OR_EQUAL:
          sym = LESS_THAN_OR_EQUAL;
          break;
        case LESS_THAN:
          sym = GREATER_THAN;
          break;
        case LESS_THAN_OR_EQUAL:
          sym = GREATER_THAN_OR_EQUAL;
          break;
        default:
          sym = this;
      }
      return sym;
    }
  }

  private final Kind kind;

  public RelationalSymbolicValue(int id, Kind kind) {
    super(id);
    this.kind = kind;
  }

  @Override
  public RelationalSymbolicValue converted(int id, SymbolicValueAdapter adapter) {
    RelationalSymbolicValue converted = new RelationalSymbolicValue(id, kind);
    converted.leftOp = adapter.convert(leftOp);
    converted.rightOp = adapter.convert(rightOp);
    return converted;
  }

  @Override
  public BooleanConstraint shouldNotInverse() {
    switch (kind) {
      case EQUAL:
      case METHOD_EQUALS:
        return BooleanConstraint.TRUE;
      default:
        return BooleanConstraint.FALSE;
    }
  }

  @Override
  public List<ProgramState> setConstraint(ProgramState initialProgramState, BooleanConstraint booleanConstraint) {
    AtomicConstraint atomic = convertToAtomic(booleanConstraint);
    if (atomic != null) {
      ProgramState state = atomic.storeInto(initialProgramState);
      return state == null ? ImmutableList.<ProgramState>of() : ImmutableList.of(state);
    }
    ProgramState programState = checkRelation(booleanConstraint, initialProgramState);
    if (programState == null) {
      return ImmutableList.of();
    }
    List<ProgramState> results = new ArrayList<>();
    List<ProgramState> copiedConstraints = copyConstraint(leftOp, rightOp, programState, booleanConstraint);
    if (Kind.METHOD_EQUALS == kind || Kind.NOT_METHOD_EQUALS == kind) {
      copiedConstraints = addNullConstraintsForBooleanWrapper(booleanConstraint, initialProgramState, copiedConstraints);
    }
    for (ProgramState ps : copiedConstraints) {
      List<ProgramState> copiedConstraintsRightToLeft = copyConstraint(rightOp, leftOp, ps, booleanConstraint);
      if (copiedConstraintsRightToLeft.size() == 1 && copiedConstraintsRightToLeft.get(0).equals(programState)) {
        results.add(programState.addConstraint(this, booleanConstraint));
      } else {
        results.addAll(copiedConstraintsRightToLeft);
      }
    }
    return results;
  }

  private List<ProgramState> addNullConstraintsForBooleanWrapper(BooleanConstraint booleanConstraint, ProgramState initialProgramState, List<ProgramState> copiedConstraints) {
    Constraint leftConstraint = initialProgramState.getConstraint(leftOp);
    Constraint rightConstraint = initialProgramState.getConstraint(rightOp);
    if (leftConstraint instanceof BooleanConstraint && rightConstraint == null && !shouldNotInverse().equals(booleanConstraint)) {
      List<ProgramState> nullConstraints = copiedConstraints.stream().map(ps -> ps.addConstraint(rightOp, ObjectConstraint.nullConstraint())).collect(Collectors.toList());
      return ImmutableList.<ProgramState>builder().addAll(copiedConstraints).addAll(nullConstraints).build();
    }
    return copiedConstraints;
  }

  @Override
  protected List<ProgramState> copyConstraint(SymbolicValue from, SymbolicValue to, ProgramState programState, BooleanConstraint booleanConstraint) {
    ProgramState newState = programState;
    if (programState.canReach(from) || programState.canReach(to)) {
      newState = programState.addConstraint(this, booleanConstraint);
    }
    return super.copyConstraint(from, to, newState, booleanConstraint);
  }

  @CheckForNull
  public ProgramState checkRelation(BooleanConstraint booleanConstraint, ProgramState programState) {
    RelationState relationState = binaryRelation().resolveState(programState.getKnownRelations());
    if (relationState.rejects(booleanConstraint)) {
      return null;
    }
    return programState;
  }

  @Override
  public BinaryRelation binaryRelation() {
    return BinaryRelation.binaryRelation(kind, leftOp, rightOp);
  }

  @Override
  public String toString() {
    return binaryRelation().toString();
  }

  public AtomicConstraint convertToAtomic(Constraint myConstraint) {
    if (!Kind.NOT_EQUAL.equals(kind) && !Kind.EQUAL.equals(kind)) {
      return null;
    }
    SymbolicValue value = null;
    Constraint constraint = null;
    if (SymbolicValue.PROTECTED_SYMBOLIC_VALUES.contains(leftOp)) {
      value = rightOp;
      constraint = ProgramState.EMPTY_STATE.getConstraint(leftOp);
    } else if (SymbolicValue.PROTECTED_SYMBOLIC_VALUES.contains(rightOp)) {
      value = leftOp;
      constraint = ProgramState.EMPTY_STATE.getConstraint(rightOp);
    }
    if (myConstraint != null) {
      return createAtomicConstraint(value, constraint, myConstraint);
    }
    return null;
  }

  private AtomicConstraint createAtomicConstraint(SymbolicValue value, Constraint constraint, Constraint oConstraint) {
    Constraint myConstraint = oConstraint;
    if (Kind.NOT_EQUAL.equals(kind)) {
      myConstraint = ((BooleanConstraint) myConstraint).inverse();
    }
    if (constraint instanceof ObjectConstraint) {
      if (BooleanConstraint.TRUE.equals(myConstraint)) {
        return new AtomicConstraint(value, constraint);
      } else {
        return new AtomicConstraint(value, ((ObjectConstraint) constraint).inverse());
      }
    }
    return null;
  }

  public static class AtomicConstraint {

    private final SymbolicValue value;
    private final Constraint constraint;

    public AtomicConstraint(SymbolicValue value, Constraint constraint) {
      this.value = value;
      this.constraint = constraint;
    }

    public boolean isNull() {
      return constraint.isNull();
    }

    public SymbolicValue getValue() {
      return value;
    }

    public void storeInto(Map<SymbolicValue, Constraint> constraints) {
      constraints.put(value, constraint);
    }

    public ProgramState storeInto(ProgramState state) {
      Constraint previous = state.getConstraint(value);
      if (previous != null && previous.isNull() ^ constraint.isNull()) {
        return null;
      }
      return state.addConstraint(value, constraint);
    }

    // TODO there must be a better way!!!
    // This admittedly horrible hack is needed to change 2 things:
    // 1: converting into OPEN a value marked as OPEN_USED (preventing the method to return with error),
    // 2: transfer the issue location from inside the called method to the location of the call.
    public ProgramState exitMethodInto(ProgramState state, MethodInvocationTree mit) {
      if (constraint instanceof ObjectConstraint) {
        return new AtomicConstraint(value, UnclosedResourcesCheck.atMethodExit((ObjectConstraint) constraint, mit)).storeInto(state);
      }
      return storeInto(state);
    }

    @Override
    public String toString() {
      StringBuilder buffer = new StringBuilder();
      buffer.append(value);
      buffer.append("->");
      buffer.append(constraint);
      return buffer.toString();
    }
  }

  public void exchange(SymbolicValue oldValue, SymbolicValue newValue) {
    if (leftOp.equals(oldValue)) {
      leftOp = newValue;
    } else if (leftOp instanceof RelationalSymbolicValue) {
      ((RelationalSymbolicValue) leftOp).exchange(oldValue, newValue);
    }
    if (rightOp.equals(oldValue)) {
      rightOp = newValue;
    } else if (rightOp instanceof RelationalSymbolicValue) {
      ((RelationalSymbolicValue) rightOp).exchange(oldValue, newValue);
    }
  }
}
