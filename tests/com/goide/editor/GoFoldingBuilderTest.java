package com.goide.editor;

import com.goide.GoCodeInsightFixtureTestCase;

public class GoFoldingBuilderTest extends GoCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() { return "folding"; }

  private void doTest() { myFixture.testFolding(getTestDataPath() + "/" + getTestName(true) + ".go"); }

  public void testSimple() { doTest(); }
  public void testImportList() { doTest(); }
  public void testImportListWithJustSingleImportKeyword() { doTest(); }
  public void testImportListWithoutSpaceBetweenKeywordAndString() { doTest(); }
  public void testImportListWithoutSpaceBetweenKeywordAndParen() { doTest(); }
  public void testEmptyImportList() { doTest(); }
  public void testImportListWithNewLineAfterKeyword() { doTest(); }
}
