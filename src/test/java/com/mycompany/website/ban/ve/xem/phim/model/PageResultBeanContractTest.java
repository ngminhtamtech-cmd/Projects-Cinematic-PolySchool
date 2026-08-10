package com.mycompany.website.ban.ve.xem.phim.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PageResultBeanContractTest {

    @Test
    void exposesEveryPaginationFieldRequiredByJspEl() throws IntrospectionException {
        Set<String> beanProperties = Set.of(
                Introspector.getBeanInfo(PageResult.class).getPropertyDescriptors())
                .stream()
                .map(descriptor -> descriptor.getName())
                .collect(Collectors.toSet());

        Set<String> requiredByAuditLogsJsp = Set.of(
                "items",
                "page",
                "size",
                "totalItems",
                "totalPages",
                "hasPrevious",
                "hasNext");

        assertTrue(
                beanProperties.containsAll(requiredByAuditLogsJsp),
                () -> "JSP EL cannot resolve PageResult fields. Missing: "
                        + requiredByAuditLogsJsp.stream()
                                .filter(property -> !beanProperties.contains(property))
                                .sorted()
                                .toList());
    }
}
