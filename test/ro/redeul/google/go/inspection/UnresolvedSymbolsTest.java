package ro.redeul.google.go.inspection;

public class UnresolvedSymbolsTest extends GoInspectionTestCase {
    public void testIfScope() throws Exception {
        doTest();
    }

    public void testIfScope2() throws Exception {
        doTest();
    }

    public void testForWithClause() throws Exception {
        doTest();
    }

    public void testForWithRange() throws Exception {
        doTest();
    }

    public void testIota() throws Exception {
        doTest();
    }

    public void testUndefinedTypeInMethodReceiver() throws Exception {
        doTest();
    }

    public void testCgo() throws Exception {
        doTest();
    }

    public void testCreateFunction() throws Exception {
        doTest();
    }

    public void testConversionToPointerType() throws Exception {
        doTest();
    }

    public void testNullPointerImportDecl() throws Exception {
        doTest();
    }

    public void testClosuresResultParameterUnsolveBug() throws Exception {
        doTest();
    }

    public void testStructField() throws Exception {
        doTest();
    }

    /* TODO FIX TEST
    public void testGH865() throws Exception {
        doTest();
    }
    public void testGH858() throws Exception {
        doTestWithDirectory();
    }
    */
}
