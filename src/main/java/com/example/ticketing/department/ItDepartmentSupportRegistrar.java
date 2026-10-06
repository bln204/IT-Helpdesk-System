package com.example.ticketing.department;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Wires the {@link ItDepartmentResolver} bean into {@link ItDepartmentSupport}'s non-Spring
 * holder so that {@link com.example.ticketing.auth.UserAccount}'s compatibility accessors stay
 * correct even when invoked outside a managed context.
 *
 * <p>This is a Phase 1 plumbing class and exists only to make the centralization work without
 * requiring UserAccount to be a Spring bean (it is a JPA entity and must not be).
 */
@Component
class ItDepartmentSupportRegistrar implements ApplicationContextAware {

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        ItDepartmentSupport.ItDepartmentSupportHolder.resolver =
            applicationContext.getBean(ItDepartmentResolver.class);
    }
}