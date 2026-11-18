package com.wherelee.edu.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 内核模块边界回归测试。
 *
 * <p>btry 是通用基座：platform / contracts / identity / payment 四层，依赖方向必须是
 * 「identity,payment -> contracts -> platform」，任何反向依赖都会让构建变红。</p>
 */
@AnalyzeClasses(packages = "com.wherelee.edu", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule platform_should_only_depend_on_itself =
            noClasses().that().resideInAPackage("..modules.platform..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.contracts..",
                            "..modules.identity..",
                            "..modules.payment..",
                            "..app..",
                            "..bootstrap.."
                    );

    @ArchTest
    static final ArchRule contracts_should_only_depend_on_platform =
            noClasses().that().resideInAPackage("..modules.contracts..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.identity..",
                            "..modules.payment..",
                            "..app..",
                            "..bootstrap.."
                    );

    @ArchTest
    static final ArchRule identity_should_not_depend_on_payment_or_app =
            noClasses().that().resideInAPackage("..modules.identity..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.payment..",
                            "..app..",
                            "..bootstrap.."
                    );

    @ArchTest
    static final ArchRule payment_should_not_depend_on_identity_or_app =
            noClasses().that().resideInAPackage("..modules.payment..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.identity..",
                            "..app..",
                            "..bootstrap.."
                    );

    /**
     * 演示宿主的运营端只能通过契约访问内核能力，不能直接依赖内核实现类。
     */
    @ArchTest
    static final ArchRule app_should_only_use_kernel_contracts =
            noClasses().that().resideInAPackage("..app..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.identity..",
                            "..modules.payment.."
                    );

    @ArchTest
    static final ArchRule kernel_should_not_depend_on_host_app =
            noClasses().that().resideInAnyPackage(
                            "..modules.platform..",
                            "..modules.contracts..",
                            "..modules.identity..",
                            "..modules.payment..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..app..",
                            "..bootstrap.."
                    );
}
