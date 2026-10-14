package com.example.ticketing.ai.query;

import com.example.ticketing.category.Category;
import com.example.ticketing.category.CategoryRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public class CategoryQueryAdapter extends JpaQueryAdapter<Category> {

    /** CategoryController#listCategories has no @PreAuthorize: any authenticated user. */
    static final AccessRule ACCESS = AccessRule.allRoles();

    static final DomainDef DEF = new DomainDef("category", "danh mục ticket",
            "danh mục và danh mục con dùng để phân loại ticket",
            List.of(
                    FieldDef.text("code", "code", "mã danh mục, vd SOFTWARE"),
                    FieldDef.text("name", "name", "tên danh mục"),
                    FieldDef.text("parent", "parent.name", "danh mục cha"),
                    FieldDef.bool("enabled", "enabled", "đang dùng")),
            "name", false);

    private final CategoryRepository repository;

    public CategoryQueryAdapter(CategoryRepository repository) {
        this.repository = repository;
    }

    @Override public DomainDef definition() { return DEF; }
    @Override public AccessRule accessRule() { return ACCESS; }
    @Override protected JpaSpecificationExecutor<Category> repository() { return repository; }
    @Override protected String idPath() { return "code"; }

    @Override
    protected Row toRow(Category c) {
        return new Row(c.getCode(), c.getName(), fields(
                "danh mục cha", c.getParent() == null ? null : c.getParent().getName(),
                "đang dùng", c.isEnabled() ? "có" : "không"),
                c.getDescription());
    }
}
