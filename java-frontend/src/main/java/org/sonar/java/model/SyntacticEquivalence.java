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
package org.sonar.java.model;

import com.google.common.base.Objects;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.PrimitiveTypeTree;
import org.sonar.plugins.java.api.tree.SyntaxToken;
import org.sonar.plugins.java.api.tree.Tree;

import javax.annotation.Nullable;

import java.util.Iterator;
import java.util.List;

public final class SyntacticEquivalence {

  private SyntacticEquivalence() {
  }

  /**
   * @return true, if nodes are syntactically equivalent
   */
  public static boolean areEquivalent(List<? extends Tree> leftList, List<? extends Tree> rightList) {
    if (leftList.size() != rightList.size()) {
      return false;
    }
    for (int i = 0; i < leftList.size(); i++) {
      Tree left = leftList.get(i);
      Tree right = rightList.get(i);
      if (!areEquivalent(left, right)) {
        return false;
      }
    }
    return true;
  }

  /**
  * @return true, if nodes are syntactically equivalent
  */
  public static boolean areEquivalent(@Nullable Tree leftNode, @Nullable Tree rightNode) {
    return areEquivalent((JavaTree) leftNode, (JavaTree) rightNode);
  }

  private static boolean areEquivalent(@Nullable JavaTree leftNode, @Nullable JavaTree rightNode) {
    if (leftNode == rightNode) {
      return true;
    }
    if (leftNode == null || rightNode == null) {
      return false;
    }
    if (leftNode.kind() != rightNode.kind() || leftNode.is(Tree.Kind.OTHER)) {
      return false;
    } else if (leftNode.isLeaf()) {
      return areLeafsEquivalent(leftNode, rightNode);
    }
    Iterator<Tree> iteratorA = leftNode.getChildren().iterator();
    Iterator<Tree> iteratorB = rightNode.getChildren().iterator();

    while (iteratorA.hasNext() && iteratorB.hasNext()) {
      if (!areEquivalent(iteratorA.next(), iteratorB.next())) {
        return false;
      }
    }

    return !iteratorA.hasNext() && !iteratorB.hasNext();
  }

  /**
   * Caller must guarantee that nodes of the same kind.
   */
  private static boolean areLeafsEquivalent(JavaTree leftNode, JavaTree rightNode) {
    if (leftNode instanceof IdentifierTree) {
      return Objects.equal(((IdentifierTree) leftNode).name(), ((IdentifierTree) rightNode).name());
    } else if (leftNode instanceof PrimitiveTypeTree) {
      return Objects.equal(((PrimitiveTypeTree) leftNode).keyword().text(), ((PrimitiveTypeTree) rightNode).keyword().text());
    } else if (leftNode instanceof SyntaxToken) {
      return Objects.equal(((SyntaxToken) leftNode).text(), ((SyntaxToken) rightNode).text());
    } else if (leftNode.is(Tree.Kind.INFERED_TYPE)) {
      return rightNode.is(Tree.Kind.INFERED_TYPE);
    } else {
      throw new IllegalArgumentException();
    }
  }

}
