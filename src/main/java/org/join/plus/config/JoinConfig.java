package org.join.plus.config;

import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.join.plus.mapper.JoinMapper;

import java.io.Serializable;
import java.util.Collection;

/**
 * @author suyun
 * @date 2021-07-27 16:09
 */
public interface JoinConfig extends StringPool {

    /**
     * 获取默认的分页
     *
     * @param <T> 分页对象类型
     * @return 返回
     */
    default <T> Page<T> defaultPage() {
        return new Page<>();
    }

    /**
     * 获取租户字段名称，数据库字段
     * 为空则表示没有租户
     *
     * @return 返回
     */
    default String tenantColumn() {
        return EMPTY;
    }

    /**
     * 获取已经实现了 {@code JoinMapper<?>} 的Mapper
     *
     * @return 返回，默认为空
     */
    default JoinMapper<?> mapper() {
        return null;
    }

    /**
     * 获取租户列表，用户租户查询
     *
     * @return 返回
     */
    default Collection<Serializable> tenants() {
        return null;
    }

    /**
     * 获取租户的基类，当实体类继承此类时，说明该实体类支持租户
     *
     * @return 返回租户的基类，默认为空
     */
    default Class<?> tenantClass() {
        return null;
    }

    /**
     * 全局配置是否有逻辑删除字段
     *
     * @return true 有逻辑删除，false 无逻辑删除
     */
    default boolean hasLogicDelete() {
        return false;
    }

    /**
     * 默认实现一个配置
     */
    class DefaultJoinConfig implements JoinConfig {
    }
}
