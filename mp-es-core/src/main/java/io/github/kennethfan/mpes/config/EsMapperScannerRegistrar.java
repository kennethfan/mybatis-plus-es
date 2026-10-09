package io.github.kennethfan.mpes.config;

import io.github.kennethfan.mpes.annotation.EsMapperScan;
import io.github.kennethfan.mpes.core.EsBaseMapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.Aware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@link EsMapperScan} 的注册器：扫描指定包下所有继承 EsBaseMapper 的接口，
 * 逐个注册 {@link EsMapperFactoryBean}。
 */
public class EsMapperScannerRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware, ResourceLoaderAware {

    private static final BeanNameGenerator NAME_GENERATOR = new org.springframework.context.annotation.AnnotationBeanNameGenerator();

    private Environment environment;
    private ResourceLoader resourceLoader;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) throws BeansException {
        AnnotationAttributes attrs = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EsMapperScan.class.getName()));

        Set<String> basePackages = new LinkedHashSet<>();
        for (String pkg : attrs.getStringArray("value")) {
            if (pkg != null && !pkg.isBlank()) {
                basePackages.add(pkg.trim());
            }
        }
        for (String pkg : attrs.getStringArray("basePackages")) {
            if (pkg != null && !pkg.isBlank()) {
                basePackages.add(pkg.trim());
            }
        }
        if (basePackages.isEmpty()) {
            // 未指定包时默认扫描注解所在类的包
            basePackages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
        }

        Set<Class<?>> mapperInterfaces = scan(basePackages);
        if (mapperInterfaces.isEmpty()) {
            throw new IllegalStateException(
                    "@EsMapperScan(" + basePackages + ") 未扫描到任何继承 EsBaseMapper 的接口");
        }

        for (Class<?> mapperInterface : mapperInterfaces) {
            BeanDefinitionBuilder builder = BeanDefinitionBuilder
                    .genericBeanDefinition(EsMapperFactoryBean.class);
            builder.addConstructorArgValue(mapperInterface);
            builder.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
            org.springframework.beans.factory.config.BeanDefinition bd = builder.getBeanDefinition();
            registry.registerBeanDefinition(NAME_GENERATOR.generateBeanName(bd, registry), bd);
        }
    }

    /** 扫描直接/间接继承 EsBaseMapper 的业务 Mapper 接口（排除 EsBaseMapper 自身） */
    private Set<Class<?>> scan(Set<String> basePackages) {
        // 默认 Provider 只匹配具体类，这里放宽为"独立接口"
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false, environment) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        return beanDefinition.getMetadata().isInterface()
                                && beanDefinition.getMetadata().isIndependent();
                    }
                };
        scanner.setResourceLoader(resourceLoader);
        scanner.addIncludeFilter(new AssignableTypeFilter(EsBaseMapper.class));

        Set<Class<?>> result = new LinkedHashSet<>();
        for (String pkg : basePackages) {
            for (var bd : scanner.findCandidateComponents(pkg)) {
                Class<?> clazz = ClassUtils.resolveClassName(bd.getBeanClassName(), resourceLoader.getClassLoader());
                if (clazz != EsBaseMapper.class) {
                    result.add(clazz);
                }
            }
        }
        return result;
    }
}
