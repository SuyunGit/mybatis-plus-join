package org.join.plus.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.join.plus.query.QueryJoin;

import java.util.List;
import java.util.Map;

/**
 * 想要使用关联查询构造器，就必须实现此mapper
 * <p>
 * 实现一次，任何类型的查询构造器都可以使用；全部继承此mapper也无所谓
 *
 * @author suyun
 * @date 2021-07-27 16:19
 */
public interface JoinMapper<T> extends BaseMapper<T> {

    /**
     * 查询并返回一个结果，如果查询到多个，则抛出异常
     * <p>
     * 需要注意的是，QueryJoin的代理名称用 {@code Constants.WRAPPER}，
     * 那么如果自定义实现的时候，这个代理名称必须保持统一
     *
     * @param wrapper 关联查询的条件构造器
     * @return 返回
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(1) FROM ${ew.from} ${ew.customSqlSegment}")
    int count(@Param(Constants.WRAPPER) QueryJoin<?> wrapper);

    /**
     * 查询并返回一个结果，如果查询到多个，则抛出异常
     * <p>
     * 需要注意的是，QueryJoin的代理名称用 {@code Constants.WRAPPER}，
     * 那么如果自定义实现的时候，这个代理名称必须保持统一
     *
     * @param wrapper 关联查询的条件构造器
     * @return 返回
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT ${ew.sqlSelect} FROM ${ew.from} ${ew.customSqlSegment}")
    Map<String, Object> oneMap(@Param(Constants.WRAPPER) QueryJoin<?> wrapper);

    /**
     * 查询并返回
     * <p>
     * 需要注意的是，QueryJoin的代理名称用 {@code Constants.WRAPPER}，
     * 那么如果自定义实现的时候，这个代理名称必须保持统一
     *
     * @param wrapper 关联查询的条件构造器
     * @return 返回
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT ${ew.sqlSelect} FROM ${ew.from} ${ew.customSqlSegment}")
    List<Map<String, Object>> listMap(@Param(Constants.WRAPPER) QueryJoin<?> wrapper);

    /**
     * 查询分页数据
     *
     * @param page    分页
     * @param wrapper 条件
     * @return 返回
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT ${ew.sqlSelect} FROM ${ew.from} ${ew.customSqlSegment}")
    Page<Map<String, Object>> pageMap(Page<?> page, @Param(Constants.WRAPPER) QueryJoin<?> wrapper);
}
