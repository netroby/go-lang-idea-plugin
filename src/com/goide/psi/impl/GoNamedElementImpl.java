/*
 * Copyright 2013-2015 Sergey Ignatov, Alexander Zolotov, Mihai Toader, Florin Patan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.goide.psi.impl;

import com.goide.GoIcons;
import com.goide.psi.*;
import com.goide.sdk.GoSdkUtil;
import com.goide.stubs.GoNamedStub;
import com.goide.stubs.GoTypeStub;
import com.goide.util.GoUtil;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.RowIcon;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class GoNamedElementImpl<T extends GoNamedStub<?>> extends GoStubbedElementImpl<T> implements GoCompositeElement, GoNamedElement {

  public GoNamedElementImpl(@NotNull T stub, @NotNull IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public GoNamedElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public boolean isPublic() {
    if (GoPsiImplUtil.builtin(this)) return true;
    T stub = getStub();
    return stub != null ? stub.isPublic() : StringUtil.isCapitalized(getName());
  }

  @Nullable
  @Override
  public PsiElement getNameIdentifier() {
    return getIdentifier();
  }

  @Nullable
  @Override
  public String getName() {
    T stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    PsiElement identifier = getIdentifier();
    return identifier != null ? identifier.getText() : null;
  }
  
  @Nullable
  @Override
  public String getQualifiedName() {
    String name = getName();
    if (name == null) return null;
    String packageName = getContainingFile().getPackageName();
    return StringUtil.isNotEmpty(packageName) ? packageName + "." + name : name;
  }

  @Override
  public int getTextOffset() {
    PsiElement identifier = getIdentifier();
    return identifier != null ? identifier.getTextOffset() : super.getTextOffset();
  }

  @NotNull
  @Override
  public PsiElement setName(@NonNls @NotNull String newName) throws IncorrectOperationException {
    PsiElement identifier = getIdentifier();
    if (identifier != null) {
      identifier.replace(GoElementFactory.createIdentifierFromText(getProject(), newName));
    }
    return this;
  }

  @Nullable
  @Override
  public GoType getGoType(@Nullable final ResolveState context) {
    if (context != null) return getGoTypeInner(context);
    return CachedValuesManager.getCachedValue(this, new CachedValueProvider<GoType>() {
      @Nullable
      @Override
      public Result<GoType> compute() {
        return Result.create(getGoTypeInner(null), PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  @Nullable
  protected GoType getGoTypeInner(@Nullable ResolveState context) {
    return findSiblingType();
  }

  @Nullable
  @Override
  public GoType findSiblingType() {
    T stub = getStub();
    if (stub != null) {
      PsiElement parent = getParentByStub();
      // todo: cast is weird
      return parent instanceof GoStubbedElementImpl ?
             (GoType)((GoStubbedElementImpl)parent).findChildByClass(GoType.class, GoTypeStub.class) :
             null;
    }
    return PsiTreeUtil.getNextSiblingOfType(this, GoType.class);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return GoCompositeElementImpl.processDeclarationsDefault(this, processor, state, lastParent, place);
  }

  @Override
  public ItemPresentation getPresentation() {
    final String text = UsageViewUtil.createNodeText(this);
    if (text != null) {
      return new ItemPresentation() {
        @Nullable
        @Override
        public String getPresentableText() {
          return getName();
        }

        @Nullable
        @Override
        public String getLocationString() {
          GoFile file = getContainingFile();
          String fileName = file.getName();
          String importPath = ObjectUtils.chooseNotNull(GoSdkUtil.getImportPath(file.getContainingDirectory()), file.getPackageName());
          return "in " + (importPath != null ? importPath  + "/" + fileName : fileName);
        }

        @Nullable
        @Override
        public Icon getIcon(boolean b) {
          return GoNamedElementImpl.this.getIcon(Iconable.ICON_FLAG_VISIBILITY);
        }
      };
    }
    return super.getPresentation();
  }

  @Nullable
  @Override
  public Icon getIcon(int flags) {
    Icon icon = null;
    if (this instanceof GoMethodDeclaration) icon = GoIcons.METHOD;
    else if (this instanceof GoFunctionDeclaration) icon = GoIcons.FUNCTION;
    else if (this instanceof GoTypeSpec) icon = GoIcons.TYPE;
    else if (this instanceof GoVarDefinition) icon = GoIcons.VARIABLE;
    else if (this instanceof GoConstDefinition) icon = GoIcons.CONSTANT;
    else if (this instanceof GoFieldDefinition) icon = GoIcons.FIELD;
    else if (this instanceof GoMethodSpec) icon = GoIcons.METHOD;
    else if (this instanceof GoAnonymousFieldDefinition) icon = GoIcons.FIELD;
    else if (this instanceof GoParamDefinition) icon = GoIcons.PARAMETER;
    else if (this instanceof GoLabelDefinition) icon = GoIcons.LABEL;
    if (icon != null) {
      if ((flags & Iconable.ICON_FLAG_VISIBILITY) != 0) {
        final RowIcon rowIcon = ElementBase.createLayeredIcon(this, icon, flags);
        rowIcon.setIcon(isPublic() ? PlatformIcons.PUBLIC_ICON : PlatformIcons.PRIVATE_ICON, 1);
        return rowIcon;
      }
      return icon;
    }
    return super.getIcon(flags);
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    if (isPublic()) {
      Module module = ModuleUtilCore.findModuleForPsiElement(this);
      return module != null ? GoUtil.moduleScope(getProject(), module) : super.getUseScope();
    }
    else {
      if (this instanceof GoVarDefinition || this instanceof GoConstDefinition) {
        GoBlock block = PsiTreeUtil.getParentOfType(this, GoBlock.class);
        if (block != null) return new LocalSearchScope(block);
      }
      return GoPsiImplUtil.packageScope(getContainingFile());
    }
  }

  @Override
  public boolean isBlank() {
    return StringUtil.equals(getName(), "_");
  }

  @Override
  public boolean shouldGoDeeper() {
    return true;
  }
}
