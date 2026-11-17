package com.wherelee.edu.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模块边界回归测试。
 *
 * <p>这里用测试代替文档约定，防止后续开发重新把实现细节穿透到其他模块。</p>
 */
@AnalyzeClasses(packages = "com.wherelee.edu", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule platform_should_not_depend_on_business_modules =
            noClasses().that().resideInAPackage("..modules.platform..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.identity..",
                            "..modules.course..",
                            "..modules.learning..",
                            "..modules.commerce..",
                            "..modules.operations.."
                    );

    @ArchTest
    static final ArchRule identity_should_only_depend_on_platform =
            noClasses().that().resideInAPackage("..modules.identity..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.course..",
                            "..modules.learning..",
                            "..modules.commerce..",
                            "..modules.operations.."
                    );

    /**
     * learning 可以从课程域/权益域的 api 契约取数（学习进度需要判断"这是哪个视频、有没有权限"），
     * 但不得依赖它们的实现包；身份/运营域仍然完全禁止。
     */
    @ArchTest
    static final ArchRule learning_should_only_depend_on_other_modules_api =
            noClasses().that().resideInAPackage("..modules.learning..")
                    .should().dependOnClassesThat(
                            JavaClass.Predicates.resideInAnyPackage(
                                    "..modules.identity..", "..modules.operations..")
                                    .or(JavaClass.Predicates.resideInAnyPackage(
                                                    "..modules.course..", "..modules.commerce..")
                                            .and(JavaClass.Predicates.resideOutsideOfPackage("..modules.course.api.."))
                                            .and(JavaClass.Predicates.resideOutsideOfPackage("..modules.commerce.api.."))));

    @ArchTest
    static final ArchRule commerce_should_not_depend_on_operations =
            noClasses().that().resideInAPackage("..modules.commerce..")
                    .should().dependOnClassesThat().resideInAPackage("..modules.operations..");

    @ArchTest
    static final ArchRule course_series_should_use_playback_contracts_not_implementations =
            noClasses().that().resideInAPackage("..modules.course.series..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.learning.playback..",
                            "..modules.platform.storage.PlaybackUrlCache"
                    );

    @ArchTest
    static final ArchRule operations_should_not_touch_commerce_repositories_or_compensation_services =
            noClasses().that().resideInAPackage("..modules.operations.admin..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.commerce.order.OrderRepository",
                            "..modules.commerce.payment.PaymentRepository",
                            "..modules.commerce.order.OrderPaymentService",
                            "..modules.commerce.order.OrderRefundService",
                            "..modules.commerce.order.OrderFulfillmentService"
                    );

    /**
     * R5（P2 支付反转）：payment 包只能依赖自身、api 与 platform；
     * 禁止再依赖 order/entitlement 的实现——依赖方向必须是「业务 → 支付」。
     */
    @ArchTest
    static final ArchRule payment_should_not_depend_on_order_or_entitlement =
            noClasses().that().resideInAPackage("..modules.commerce.payment..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.commerce.order..",
                            "..modules.commerce.entitlement.."
                    );

    @ArchTest
    static final ArchRule operations_should_not_touch_identity_internals =
            noClasses().that().resideInAPackage("..modules.operations.admin..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.identity.user..",
                            "..modules.identity.security.."
                    );

    @ArchTest
    static final ArchRule operations_should_not_touch_course_governance_internals =
            noClasses().that().resideInAPackage("..modules.operations.admin..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.course.governance..",
                            "..modules.course.teacher..",
                            "..modules.course.series.."
                    );
}