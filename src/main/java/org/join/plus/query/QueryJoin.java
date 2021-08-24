package org.join.plus.query;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.SharedString;
import com.baomidou.mybatisplus.core.conditions.query.Query;
import com.baomidou.mybatisplus.core.conditions.segments.MergeSegments;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.*;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.join.plus.common.*;
import org.join.plus.config.JoinConfig;
import org.join.plus.mapper.JoinMapper;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 多表关联查询器，无需配置xml，直接构建执行即可
 * <li>1.只能进行平级多表关联构建，不支持子查询构建，如果需要子查询，需要通过自定义sql拼接的方式</li>
 * <li>2.虽然本类基本重写了所有的条件函数，但为了方便使用，只推荐使用函数式</li>
 * <li>3.本查询器支持WHERE和常用的JOIN关联方式进行关联查询</li>
 * <li>4.查询返回结果的实体默认是Map类型的</li>
 * <li>5.暂不支持having的操作</li>
 * <li>6.需要注意的是如果使用{@code whereJoin(Class<?>)}进行了关联，则再使用{@code join}相关的关联时，关联的表将被关联到上一个{@code whereJoin(Class<?>)}的对象后面</li>
 * <li>7.需要注意的是查询器的泛型为主查询实体，构建之后默认查询全部的字段，再关联其它实体时，默认不查询任何字段，
 * 如果选择对某实体查询全部字段后又指定该实体具体字段，则为设定查询字段，将只查询指定的字段，用户可以根据{@code selectAll(Class<?>)}和{@code selectNone(Class<?>)}对想要查询的字段进行调整，
 * 如果查询为空，最后会直接生成 * 进行查询所有</li>
 * <li>8.同一个实体属性可以多次查询，但需要保证别名不会重复，否则会异常</li>
 *
 * <p>
 * 默认对初始创建的实体查询全部字段
 * 使用示例1："SELECT {baseUser的全部字段} FROM base_user AS baseUser WHERE (TRUE AND baseUser.tenant_id IN (?, ?, ?) AND baseUser.deleted = 1)"
 * <pre>
 *     QueryJoin.create(BaseUser.class);
 * </pre>
 *
 * <p>
 * 如果指定了某个实体的查询字段，则会覆盖默认设置，只查询这些指定字段
 * 使用示例2："SELECT baseUser.user_id AS userId,baseUser.username AS username,baseUser.nickname AS nickname FROM base_user AS baseUser WHERE (TRUE AND baseUser.user_id = 1 AND baseUser.deleted = '0' AND baseUser.tenant_id IN (?, ?, ?)) ORDER BY baseUser.user_id ASC GROUP BY baseUser.user_id"
 * <pre>
 *     QueryJoin.create(BaseUser.class)
 *         .selects(BaseUser::getUserId, BaseUser::getUsername, BaseUser::getNickname)
 *         .eq(BaseUser::getUserId, 1)
 *         .orderByAsc(BaseUser::getUserId)
 *         .groupBy(BaseUser::getUserId);
 * </pre>
 *
 * <p>
 * 使用where条件进行两表关联
 * 使用示例3："SELECT baseUser.user_id AS userId,baseUser.username AS username,baseUser.nickname AS nickname,baseRole.role_id AS roleId, baseRole.role_name AS roleName, baseRole.role_type AS roleType FROM base_user AS baseUser, base_role AS baseRole WHERE (TRUE AND baseUser.role_id = baseRole.role_id AND baseUser.username LIKE '%123%' AND baseRole.role_id IN (1,2,3) AND baseUser.deleted = '0' AND baseUser.tenant_id IN (?, ?, ?) AND baseRole.tenant_id IN (?, ?, ?) AND baseRole.deleted = '0')"
 * <pre>
 *     QueryJoin.create(BaseUser.class)
 *         .selects(BaseUser::getUserId, BaseUser::getUsername, BaseUser::getNickname)
 *         .whereJoin(BaseRole.class)
 *         .on(BaseUser::getRoleId, BaseRole::getRoleId)
 *         .selects(BaseRole::getRoleId, BaseRole::getRoleName, BaseRole::getRoleType)
 *         .like(BaseUser::getUsername, "123")
 *         .in(BaseRole::getRoleId, 1,2,3)
 * </pre>
 *
 * <p>
 * 当上一步使用where进行关联之后，再使用join关联时，则join的表只会关联到上一个where表上
 * 使用示例4："SELECT {baseUser的全部字段} FROM base_user AS baseUser, base_role AS baseRole LEFT JOIN base_menu AS baseMenu ON baseRole.menu_id = baseMenu.menu_id WHERE (TRUE AND baseUser.role_id = baseRole.role_id AND baseUser.deleted = '0' AND baseUser.tenant_id IN (?, ?, ?) AND baseRole.tenant_id IN (?, ?, ?) AND baseRole.deleted = '0')"
 * <pre>
 *     QueryJoin.create(BaseUser.class)
 *         .whereJoin(BaseRole.class)
 *         .on(BaseUser::getRoleId, BaseRole::getRoleId)
 *         .leftJoin(BaseMenu.class)
 *         .on(BaseRole::getMenuId, BaseMenu::getMenuId);
 * </pre>
 *
 * <p>
 * 可以设置分页工具进行分页查询
 * 使用示例5："SELECT {baseUser全部字段} FROM base_user AS baseUser WHERE (TRUE AND baseUser.deleted = '0' AND baseUser.tenant_id IN (?, ?, ?)) LIMIT 0, 10"
 * <pre>
 *     QueryJoin.create(BaseUser.class).page(new Pager<>());
 * </pre>
 *
 * <p>
 * 可以启用 DISTINCT关键字进行查询
 * 使用示例6："SELECT DISTINCT {baseUser全部字段} FROM base_user AS baseUser WHERE (TRUE AND baseUser.deleted = '0' AND baseUser.tenant_id IN (?, ?, ?))"
 * <pre>
 *     QueryJoin.create(BaseUser.class).enableDistinct();
 * </pre>
 *
 * <p>
 * 可以同时启用DISTINCT和关闭租户进行查询
 * 使用示例7："SELECT DISTINCT {baseUser全部字段} FROM base_user AS baseUser WHERE (TRUE AND baseUser.deleted = '0')"
 * <pre>
 *     QueryJoin.create(BaseUser.class).enableDistinct().disableTenant();
 * </pre>
 *
 * <p>
 * 可以同时启用DISTINCT和关闭租户进行查询和关闭逻辑删除查询
 * 使用示例8："SELECT DISTINCT {baseUser全部字段} FROM base_user AS baseUser WHERE (TRUE)"
 * <pre>
 *     QueryJoin.create(BaseUser.class).enableDistinct().disableTenant().disableLogicDelete();
 * </pre>
 *
 * <p>
 * 当指定了某个实体的租户条件和逻辑删除条件时，则会覆盖默认设置
 * 使用示例9："SELECT {baseUser全部字段} FROM base_user AS baseUser WHERE (TRUE AND baseUser.deleted = 1 AND baseUser.tenant_id = 1)"
 * <pre>
 *     QueryJoin.create(BaseUser.class)
 *         .eq(BaseUser::getDeleted, 1)
 *         .eq(BaseUser::getTenantId, 1);
 * </pre>
 *
 * <p>
 * 当对BaseUser创建了关联查询并关联了BaseRole时，可以选择不查询BaseUser，只查询BaseRole
 * 使用示例10："SELECT {baseRole全部字段} FROM base_user AS baseUser JOIN base_role AS baseRole ON baseUser.role_id = baseRole.role_id WHERE (TRUE)"
 * <pre>
 *     QueryJoin.create(BaseUser.class)
 *         .selectNone(BaseUser.class)
 *         .join(BaseRole.class)
 *         .on(BaseUser::getRoleId, BaseRole::getRoleId)
 *         .selectAll(BaseRole.class)
 *         .disableTenant()
 *         .disableLogicDelete();
 * </pre>
 *
 * <p>
 * 使用{@code selectAll()}不指定实体时，表示查询全部实体的全部字段
 * 同样使用{@code selectNone()}不指定实体时，表示任何一个实体的字段都不查询
 * 使用示例11："SELECT {baseUser全部字段,baseRole全部字段} FROM base_user AS baseUser JOIN base_role AS baseRole ON baseUser.role_id = baseRole.role_id WHERE (TRUE)"
 * <pre>
 *     QueryJoin.create(BaseUser.class)
 *         .join(BaseRole.class)
 *         .on(BaseUser::getRoleId, BaseRole::getRoleId)
 *         .selectAll()
 *         .disableTenant()
 *         .disableLogicDelete();
 * </pre>
 *
 * <p>
 * 当使用{@code selectNone()}不指定实体时，表示任何一个实体的字段都不查询；如果使用字符串的形式指定了查询字段，则不受{@code selectNone()}的影响
 * 使用示例12："SELECT baseUser.username,baseRole.roleId FROM base_user AS baseUser JOIN base_role AS baseRole ON baseUser.role_id = baseRole.role_id WHERE (TRUE)"
 * <pre>
 *     QueryJoin.create(BaseUser.class)
 *         .join(BaseRole.class)
 *         .on(BaseUser::getRoleId, BaseRole::getRoleId)
 *         .selectNone()
 *         .disableTenant()
 *         .disableLogicDelete()
 *         .select("baseUser.username", "baseRole.roleId")
 * </pre>
 *
 * @author suyun
 * @date 2021-07-16 17:54
 */
@Slf4j
@SuppressWarnings("unused")
public class QueryJoin<M extends Model<M>> extends AbstractWrapper<M, String, QueryJoin<M>>
        implements Query<QueryJoin<M>, M, String>, Serializable {
    private final static long serialVersionUID = 1L;

    /**
     * 已经加入查询的表
     * key：类名，全类名
     * value：表信息
     */
    @Getter
    private final Map<String, JoinTableInfo> tableMap = CollectionUtils.newHashMap();

    /**
     * 缓存所有已经加入查询的字段
     * key：查询的字段别名，因为别名不可重复
     * value：查询的表别名和字段，ex：tableAlias
     */
    private final Map<String, String> selectOthers = CollectionUtils.newHashMap();

    /**
     * 关联查询的配置
     */
    private final JoinConfig joinConfig;

    /**
     * 主表，排在第一位的，必须要有
     */
    private final JoinTableInfo master;

    /**
     * From的语句，其中包含关联语句和On语句
     */
    private final StringBuilder sqlFrom = new StringBuilder();

    /**
     * 分页
     */
    private Page<?> page;

    /**
     * 是否关闭租户查询，默认不关闭
     * 如果不关闭，则只要有租户属性，都会追加租户查询条件
     */
    private boolean disableTenant = false;

    /**
     * 是否使用 DISTINCT 关键字，默认不启用
     */
    private boolean enableDistinct = false;

    /**
     * 是否关闭逻辑删除查询，默认不关闭
     * 如果不关闭，默认追加逻辑未删除条件
     * 当手动指定了逻辑删除条件，则不再自动追加逻辑删除条件
     */
    private boolean disableLogicDelete = false;

    /**
     * 临时存放结果，默认为空
     */
    private List<Map<String, Object>> listResult;

    /**
     * 临时存放分页的看结果，默认为空
     */
    private Page<Map<String, Object>> pageResult;

    /**
     * 隐藏构造函数
     *
     * @param master 主表
     */
    private QueryJoin(JoinTableInfo master, JoinConfig joinConfig) {
        this.master = master;
        this.joinConfig = joinConfig;
        super.initNeed();
        if (joinConfig != null) {
            this.disableTenant = joinConfig.tenantClass() == null || StrUtil.isBlank(joinConfig.tenantColumn());
            this.disableLogicDelete = !joinConfig.hasLogicDelete();
        }
    }

    /**
     * 非对外公开的构造方法,只用于生产嵌套 sql
     *
     * @param entityClass 本不应该需要的
     */
    private QueryJoin(JoinTableInfo master, JoinConfig joinConfig, M entity, Class<M> entityClass, AtomicInteger paramNameSeq,
                      Map<String, Object> paramNameValuePairs, MergeSegments mergeSegments,
                      SharedString lastSql, SharedString sqlComment, SharedString sqlFirst) {
        super.setEntity(entity);
        super.setEntityClass(entityClass);
        this.paramNameSeq = paramNameSeq;
        this.paramNameValuePairs = paramNameValuePairs;
        this.expression = mergeSegments;
        this.lastSql = lastSql;
        this.sqlComment = sqlComment;
        this.sqlFirst = sqlFirst;
        this.master = master;
        this.joinConfig = joinConfig;
        super.initNeed();
        if (joinConfig != null) {
            this.disableTenant = joinConfig.tenantClass() == null || StrUtil.isBlank(joinConfig.tenantColumn());
            this.disableLogicDelete = !joinConfig.hasLogicDelete();
        }
    }

    /**
     * 创建关联查询，需要先从创建主表开始
     *
     * @param masterTableInfo 主表类
     * @param <M>             类型
     * @return 返回关联查询实例
     */
    public static <M extends Model<M>> QueryJoin<M> create(TableInfo masterTableInfo, JoinConfig joinConfig) {
        return new QueryJoin<>(new JoinTableInfo(masterTableInfo), joinConfig);
    }

    /**
     * 创建关联查询，需要先从创建主表开始
     *
     * @param masterTable 主表类
     * @param <M>         类型
     * @return 返回关联查询实例
     */
    public static <M extends Model<M>> QueryJoin<M> create(Class<M> masterTable) {
        return create(masterTable, null);
    }

    /**
     * 创建关联查询，需要先从创建主表开始
     *
     * @param masterTable 主表类
     * @param <M>         类型
     * @return 返回关联查询实例
     */
    public static <M extends Model<M>> QueryJoin<M> create(Class<M> masterTable, JoinConfig joinConfig) {
        TableInfo master = TableInfoHelper.getTableInfo(masterTable);
        if (master == null) {
            throw new MybatisPlusException("创建关联查询失败，无法获取表类型信息");
        }

        JoinTableInfo et = new JoinTableInfo(master, true, SelectType.ALL);
        QueryJoin<M> qj = new QueryJoin<>(et, joinConfig);
        qj.tableMap.put(masterTable.getName(), et);
        /// "master_table AS tableAlias"
        qj.sqlFrom
                .append(master.getTableName())
                .append(StrUtil.SPACE)
                .append(StrUtil.AS)
                .append(StrUtil.SPACE)
                .append(et.getAliasName());
        // 主表默认查询全部字段
        return qj;
    }

    /**
     * 关闭租户条件查询
     * <p>
     * 手动设置租户条件时，会自动关闭
     *
     * @return 返回本实例
     */
    public QueryJoin<M> disableTenant() {
        disableTenant = true;
        return this;
    }

    /**
     * 关闭逻辑删除条件查询
     * <p>
     * 手动设置逻辑删除条件时，会自动关闭
     *
     * @return 返回本实例
     */
    public QueryJoin<M> disableLogicDelete() {
        disableLogicDelete = true;
        return this;
    }

    /**
     * 开启 DISTINCT 查询
     *
     * @return 返回本实例
     */
    public QueryJoin<M> enableDistinct() {
        enableDistinct = true;
        return this;
    }

    /**
     * 查询某些实体的全部属性字段，如果参数为空，则查询所有实体的所有字段
     *
     * @param es  需要查询的实体
     * @param <E> 实体类型
     * @return 返回本示例
     */
    @SafeVarargs
    public final <E extends Model<E>> QueryJoin<M> selectAll(Class<E>... es) {
        return selectAny(SelectType.ALL, es);
    }

    /**
     * 清空某些实体的全部属性字段的查询，如果参数为空，则清空所有实体的所有字段
     * <p>
     * 不会清空手动设置了别名或自定义查询的字段
     *
     * @param es  需要查询的实体
     * @param <E> 实体类型
     * @return 返回本示例
     */
    @SafeVarargs
    public final <E extends Model<E>> QueryJoin<M> selectNone(Class<E>... es) {
        return selectAny(SelectType.NONE, es);
    }

    /**
     * 选择实体查询
     *
     * @param selectType 查询的类型
     * @param es         查询的实体
     * @param <E>        实体类型
     * @return 返回本实例
     */
    @SafeVarargs
    private final <E extends Model<E>> QueryJoin<M> selectAny(SelectType selectType, Class<E>... es) {
        Consumer<JoinTableInfo> consumer = selectType == SelectType.ALL ? JoinTableInfo::selectAll : JoinTableInfo::selectNone;
        if (ArrayUtils.isEmpty(es)) {
            this.tableMap.values()
                    .forEach(consumer);
        } else {
            Arrays.stream(es)
                    .map(e -> this.tableMap.get(e.getName()))
                    .filter(Objects::nonNull)
                    .forEach(consumer);
        }
        return this;
    }

    /**
     * 选定需要查询的字段
     *
     * @param get 字段的get函数
     * @param <S> 字段的类
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> select(SFunction<S, ?> get) {
        return select(get, null);
    }

    /**
     * 选定多个需要查询的字段
     * 只能添加相同类的多个字段
     *
     * @param gets 多个字段的get函数
     * @param <S>  字段的类
     * @return 返回本实例
     * @see QueryJoin#select(SFunction)
     */
    @SafeVarargs
    public final <S extends Model<S>> QueryJoin<M> selects(SFunction<S, ?>... gets) {
        // 不管几个参数，所属的类型都是相同的，那就先解析一个
        if (ArrayUtils.isNotEmpty(gets)) {
            ColumnInfo
                    .init(this.tableMap, gets[0])
                    .getJoinTableInfo()
                    .selectSome(gets);
        }
        return this;
    }

    /**
     * 选定需要查询的字段，并设置别名
     *
     * @param get   字段的get函数
     * @param alias 字段别名
     * @param <S>   字段的类
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> select(SFunction<S, ?> get, String alias) {
        ColumnInfo<S> ci = ColumnInfo.init(this.tableMap, get, alias);

        if (StrUtil.isBlank(alias)) {
            ci.getJoinTableInfo().selectSome(ci.getColumnName(), ci.getColumnAlias());
            return this;
        }

        if (this.selectOthers.containsKey(alias)) {
            log.warn("字段[{}]已经加入查询列表中，不再继续加入", alias);
            return this;
        }

        /// "tableAlias.column_name AS columnAlias"
        this.selectOthers.put(alias, ci.cndColumnStr());
        return this;
    }

    @Override
    protected QueryJoin<M> instance() {
        return new QueryJoin<>(master, joinConfig, getEntity(), getEntityClass(), paramNameSeq, paramNameValuePairs,
                new MergeSegments(), SharedString.emptyString(), SharedString.emptyString(), SharedString.emptyString());
    }

    /**
     * 获取select语句
     *
     * @return 返回sql select
     */
    @Override
    public String getSqlSelect() {
        StringBuilder sqlSelect = new StringBuilder(enableDistinct ? StrUtil.DISTINCT.concat(StrUtil.SPACE) : StrUtil.EMPTY);

        if (!this.selectOthers.isEmpty()) {
            this.selectOthers.forEach((k, v) -> {
                if (StrUtil.isBlank(v)) {
                    sqlSelect.append(k)
                            .append(StrUtil.COMMA);
                } else {
                    sqlSelect.append(v)
                            .append(StrUtil.SPACE)
                            .append(StrUtil.AS)
                            .append(StrUtil.SPACE)
                            .append(k)
                            .append(StrUtil.COMMA);
                }
            });
        }

        if (!this.tableMap.isEmpty()) {
            this.tableMap.values()
                    .forEach(jti -> {
                        String selectString = jti.selectString();
                        if (StrUtil.isNotBlank(selectString)) {
                            sqlSelect.append(selectString)
                                    .append(StrUtil.COMMA);
                        }
                    });
        }

        if (sqlSelect.toString().endsWith(StrUtil.COMMA)) {
            int length = sqlSelect.length();
            sqlSelect.delete(length - 1, length);
        } else {
            // 如果不是以 ',' 结尾，那肯定就是没查询任何东西
            throw new MybatisPlusException("未查询任何字段");
        }

        return sqlSelect.toString();
    }

    /**
     * 拼装条件
     *
     * @return 返回条件的sql
     */
    @Override
    public String getCustomSqlSegment() {
        if (this.tableMap.isEmpty() || (disableLogicDelete && disableTenant)) {
            return super.getCustomSqlSegment();
        }

        final List<Serializable> tenants = new ArrayList<>();
        if (!disableTenant) {
            tenants.addAll(Optional.of(Optional.of(joinConfig)
                            .orElse(new JoinConfig.DefaultJoinConfig()).tenants())
                    .orElse(new ArrayList<>(0)));
        }
        final String sql = super.getCustomSqlSegment();
        this.tableMap.values().forEach(table -> {
            if (!disableTenant) {
                Class<?> superClass = table.getTableInfo()
                        .getEntityType()
                        .getSuperclass();
                String columnStr = table.getAliasName()
                        .concat(StrUtil.DOT)
                        .concat(joinConfig.tenantColumn());
                boolean ten = superClass == joinConfig.tenantClass() && !sql.contains(columnStr);
                if (tenants.size() == 1) {
                    eq(ten, columnStr, tenants.get(0));
                } else {
                    in(ten, columnStr, tenants);
                }
            }

            if (!disableLogicDelete) {
                TableFieldInfo logicDelete = table.getLogicDeleteField();
                if (logicDelete != null) {
                    String columnStr = table.getAliasName()
                            .concat(StrUtil.DOT)
                            .concat(logicDelete.getColumn());
                    eq(!sql.contains(columnStr), columnStr, StrUtil.tryToNumber(logicDelete.getLogicNotDeleteValue()));
                }
            }
        });
        return super.getCustomSqlSegment();
    }

    /**
     * 获取 From 语句
     *
     * @return 返回
     */
    public String getFrom() {
        return sqlFrom.toString();
    }

    /**
     * 获取完整的sql语句
     * 字符串截取和拼接可能会有问题，需要寻求MyBatis-Plus的解决方式
     *
     * @return 返回
     */
    public String getFullSql() {
        Map<String, Object> pairs = this.getParamNameValuePairs();
        String targetSql = getCustomSqlSegment();
        if (CollectionUtils.isNotEmpty(pairs) && StrUtil.isNotBlank(targetSql)) {
            for (Map.Entry<String, Object> entry : pairs.entrySet()) {
                String key = entry.getKey();
                String keyWorld = String.format(Constants.WRAPPER_PARAM_FORMAT, Constants.WRAPPER, key);

                Object val = entry.getValue();
                targetSql = targetSql.replace(keyWorld, StringUtils.sqlParam(val));
            }
        }

        return StrUtil.SELECT
                .concat(enableDistinct ? StrUtil.DISTINCT.concat(StrUtil.SPACE) : StrUtil.SPACE)
                .concat(getSqlSelect())
                .concat(StrUtil.SPACE)
                .concat(StrUtil.FROM)
                .concat(StrUtil.SPACE)
                .concat(getFrom())
                .concat(StrUtil.SPACE)
                .concat(targetSql);
    }

    /**
     * 指定查询的字段，需要注意的是查询的字段前要加表别名，否则会出错，建议不直接使用此函数
     *
     * @param columns 需要查询的字段
     * @return 返回本实例
     * @see QueryJoin#select(SFunction)
     * @see QueryJoin#select(SFunction, String)
     */
    @Override
    public QueryJoin<M> select(String... columns) {
        if (ArrayUtils.isEmpty(columns)) {
            return this;
        }

        Arrays.stream(columns)
                .forEach(c -> {
                    if (this.selectOthers.containsKey(c)) {
                        log.warn("查询的字段[{}]已经存在了查询列表中", c);
                    } else {
                        this.selectOthers.put(c, StrUtil.EMPTY);
                    }
                });

        return typedThis;
    }

    /**
     * 暂时不支持这种查询
     *
     * @param entityClass 主实体
     * @param predicate   查询字段处理器
     * @return 返回本实例
     * @throws UnsupportedOperationException 永久抛出此异常，此函数暂未实现
     */
    @Override
    public QueryJoin<M> select(Class<M> entityClass, Predicate<TableFieldInfo> predicate) {
        master.getTableInfo()
                .getFieldList()
                .stream()
                .filter(predicate)
                .forEach(f -> this.master.selectSome(f.getColumn(), f.getProperty()));
        return this;
    }

    /**
     * 清空条件构造器
     */
    @Override
    public void clear() {
        super.clear();
        this.listResult = null;
        this.pageResult = null;
        this.sqlFrom.setLength(0);
        this.tableMap.clear();
        this.tableMap.put(master.getTableInfo().getEntityType().getName(), master);
    }

    /**
     * 通过 WHERE 条件进行关联
     *
     * @param tableEntity 需要关联的实体
     * @param <O>         需要关联的实体类型
     * @return 返回关联实例进行关系对应
     */
    public <O extends Model<O>> JoinOn<M, O> whereJoin(Class<O> tableEntity) {
        return joinSelect(tableEntity, JoinType.WHERE);
    }

    /**
     * 通过 JOIN 进行关联
     *
     * @param tableEntity 需要关联的实体
     * @param <O>         需要关联的实体类型
     * @return 返回关联实例进行关系对应
     */
    public <O extends Model<O>> JoinOn<M, O> join(Class<O> tableEntity) {
        return joinSelect(tableEntity, JoinType.JOIN);
    }

    /**
     * 通过 INNER JOIN 条件进行关联
     *
     * @param tableEntity 需要关联的实体
     * @param <O>         需要关联的实体类型
     * @return 返回关联实例进行关系对应
     */
    public <O extends Model<O>> JoinOn<M, O> innerJoin(Class<O> tableEntity) {
        return joinSelect(tableEntity, JoinType.INNER);
    }

    /**
     * 通过 CROSS JOIN 条件进行关联
     *
     * @param tableEntity 需要关联的实体
     * @param <O>         需要关联的实体类型
     * @return 返回关联实例进行关系对应
     */
    public <O extends Model<O>> JoinOn<M, O> crossJoin(Class<O> tableEntity) {
        return joinSelect(tableEntity, JoinType.CROSS);
    }

    /**
     * 通过 LEFT JOIN 条件进行关联
     *
     * @param tableEntity 需要关联的实体
     * @param <O>         需要关联的实体类型
     * @return 返回关联实例进行关系对应
     */
    public <O extends Model<O>> JoinOn<M, O> leftJoin(Class<O> tableEntity) {
        return joinSelect(tableEntity, JoinType.LEFT);
    }

    /**
     * 通过 RIGHT JOIN 条件进行关联
     *
     * @param tableEntity 需要关联的实体
     * @param <O>         需要关联的实体类型
     * @return 返回关联实例进行关系对应
     */
    public <O extends Model<O>> JoinOn<M, O> rightJoin(Class<O> tableEntity) {
        return joinSelect(tableEntity, JoinType.RIGHT);
    }

    /**
     * 通过 JOIN 进行关联
     *
     * @param tableEntity 需要关联的实体
     * @param <O>         需要关联的实体类型
     * @return 返回关联实例进行关系对应
     */
    public <O extends Model<O>> JoinOn<M, O> joinSelect(Class<O> tableEntity, JoinType joinType) {
        TableInfo joinInfo = TableInfoHelper.getTableInfo(tableEntity);
        if (master == null) {
            throw new MybatisPlusException("创建关联查询失败，无法获取表类型信息");
        }

        JoinTableInfo et = new JoinTableInfo(joinInfo);
        this.tableMap.put(tableEntity.getName(), et);

        if (joinType == JoinType.WHERE) {
            /// ",table_name AS aliasName"
            this.sqlFrom.append(StrUtil.COMMA);
        } else {
            String join;
            switch (joinType) {
                case JOIN:
                    join = StrUtil.JOIN;
                    break;
                case INNER:
                    join = StrUtil.INNER_JOIN;
                    break;
                case CROSS:
                    join = StrUtil.CROSS_JOIN;
                    break;
                case LEFT:
                    join = StrUtil.LEFT_JOIN;
                    break;
                case RIGHT:
                    join = StrUtil.RIGHT_JOIN;
                    break;
                default:
                    throw new UnsupportedOperationException("不支持的join操作：" + joinType);
            }

            /// " ${join} table_name AS aliasName"
            this.sqlFrom
                    .append(StrUtil.SPACE)
                    .append(join)
                    .append(StrUtil.SPACE);
        }
        this.sqlFrom
                .append(et.getTableInfo().getTableName())
                .append(StrUtil.SPACE)
                .append(StrUtil.AS)
                .append(StrUtil.SPACE)
                .append(et.getAliasName());
        return new JoinOn<>(joinType, sqlFrom, this);
    }

    private JoinMapper<?> executeCheck() {
        if (this.joinConfig == null || this.joinConfig.mapper() == null) {
            throw new MybatisPlusException("查询连接未设置");
        }

        return this.joinConfig.mapper();
    }

    /**
     * 查询一个结果并返回，如果有多个结果，则抛出异常
     *
     * @return 返回查询的结果
     */
    public Map<String, Object> oneMap() {
        return oneMap(true);
    }

    /**
     * 查询并返回
     *
     * @param onlyOne true最多只会有一个结果，如果出现多个则抛出异常，false可能会出现多个结果，但直接取第一个结果
     * @return 返回查询的结果
     */
    public Map<String, Object> oneMap(boolean onlyOne) {
        return executeCheck().oneMap(this.last(!onlyOne, "LIMIT 0,1"));
    }

    /**
     * 查询并返回
     *
     * @param onlyOne true最多只会有一个结果，如果出现多个则抛出异常，false可能会出现多个结果，但直接取第一个结果
     * @return 返回查询的结果
     */
    public Map<String, Object> oneMap(JoinMapper<?> superMapper, boolean onlyOne) {
        return superMapper.oneMap(this.last(!onlyOne, "LIMIT 0,1"));
    }

    /**
     * 查询并返回
     *
     * @return 返回查询的结果
     */
    public List<Map<String, Object>> listMap() {
        return executeCheck().listMap(this);
    }

    /**
     * 查询并返回
     *
     * @return 返回查询的结果
     */
    public List<Map<String, Object>> listMap(JoinMapper<?> superMapper) {
        return superMapper.listMap(this);
    }

    /**
     * 查询分页结果并返回
     *
     * @return 返回分页查询的结果
     */
    public Page<Map<String, Object>> pagerMap() {
        if (this.page == null) {
            this.page = new Page<>();
        }
        return executeCheck().pageMap(this.page, this);
    }

    /**
     * 查询分页结果并返回
     *
     * @return 返回分页查询的结果
     */
    public Page<Map<String, Object>> pagerMap(JoinMapper<?> superMapper) {
        if (this.page == null) {
            this.page = new Page<>();
        }
        return superMapper.pageMap(this.page, this);
    }

    /**
     * 指定分页条件进行查询
     * 查询分页结果并返回
     *
     * @return 返回分页查询的结果
     */
    public Page<Map<String, Object>> pagerMap(Page<?> page) {
        if (page == null) {
            page = new Page<>();
        }
        return executeCheck().pageMap(page, this);
    }

    /**
     * 指定分页条件进行查询
     * 查询分页结果并返回
     *
     * @return 返回分页查询的结果
     */
    public Page<Map<String, Object>> pagerMap(Page<?> page, JoinMapper<?> superMapper) {
        if (page == null) {
            page = new Page<>();
        }
        return superMapper.pageMap(page, this);
    }

    /**
     * 只查询一个，并按照主表实体返回
     * 如果查询出多个，则抛出异常
     *
     * @return 返回一个主表实体对象
     */
    public M one() {
        return one(true);
    }

    /**
     * 只查询一个，并按照主表实体返回
     *
     * @param onlyOne true如果查询到多个则会异常，false如果查询到多个，则返回第一个
     * @return 返回一个主表实体对象
     */
    @SuppressWarnings("unchecked")
    public M one(boolean onlyOne) {
        Map<String, Object> one = oneMap(onlyOne);
        if (one == null) {
            return null;
        }

        return (M) BeanUtil.mapToBean(one, master.getTableInfo().getEntityType(), true, CopyOptions.create());
    }

    /**
     * 将结果查询出来之后再填充到每个实体中
     * 直接返回主实体类型对应的列表
     */
    @SuppressWarnings("unchecked")
    public List<M> entityList() {
        if (this.listResult == null) {
            this.listResult = listMap();
        }

        if (this.listResult.isEmpty()) {
            return Collections.emptyList();
        }

        List<M> list = new ArrayList<>(this.listResult.size());
        this.listResult.forEach(map -> list.add((M) BeanUtil.mapToBean(map, master.getTableInfo().getEntityType(), true, CopyOptions.create())));

        return list;
    }

    /**
     * 获取一个实体对象的结果，这个实体对象可随意定义
     * 如果有多个结果，则抛出异常
     *
     * @param entityType 实体对象的类型
     * @param <E>        实体对象的类型
     * @return 返回一个实体对象
     */
    public <E> E oneEntity(Class<E> entityType) {
        Map<String, Object> one = oneMap();
        if (one == null) {
            return null;
        }

        return BeanUtil.mapToBean(one, entityType, true, CopyOptions.create());
    }

    /**
     * 获取一个实体对象的结果，这个实体对象可随意定义
     *
     * @param onlyOne    true如果查询到多个则会异常，false如果查询到多个，则返回第一个
     * @param entityType 实体对象的类型
     * @param <E>        实体对象的类型
     * @return 返回一个实体对象
     */
    public <E> E oneEntity(boolean onlyOne, Class<E> entityType) {
        Map<String, Object> one = oneMap(onlyOne);
        if (one == null) {
            return null;
        }

        return BeanUtil.mapToBean(one, entityType, true, CopyOptions.create());
    }

    /**
     * 将结果查询出来之后再填充到实体中
     *
     * @param listType 集合实体的类型
     */
    public <E> List<E> toEntityList(Class<E> listType) {
        if (listType == null) {
            return Collections.emptyList();
        }

        if (this.listResult == null) {
            this.listResult = listMap();
        }

        if (this.listResult.isEmpty()) {
            return Collections.emptyList();
        }

        List<E> list = new ArrayList<>(this.listResult.size());
        this.listResult.forEach(map -> list.add(BeanUtil.mapToBean(map, listType, true, CopyOptions.create())));

        return list;
    }

    /**
     * 将结果查询出来之后再填充到新的分页中
     * 直接返回主实体类型对应的page
     */
    @SuppressWarnings("unchecked")
    public Page<M> entityPage() {
        if (this.pageResult == null) {
            this.pageResult = pagerMap();
        }

        List<Map<String, Object>> records = this.pageResult.getRecords();
        if (records.isEmpty()) {
            return new Page<>();
        }

        Page<M> pager = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal(), pageResult.isSearchCount());
        List<M> list = new ArrayList<>(records.size());
        records.forEach(map -> list.add((M) BeanUtil.mapToBean(map, master.getTableInfo().getEntityType(), true, CopyOptions.create())));

        pager.setRecords(list);
        return pager;
    }

    /**
     * 将结果查询出来之后再填充到实体中
     *
     * @param pageType 集合实体的类型
     */
    public <E> Page<E> toEntityPage(Class<E> pageType) {
        if (pageType == null) {
            return new Page<>();
        }

        if (this.pageResult == null) {
            this.pageResult = pagerMap();
        }

        List<Map<String, Object>> records = this.pageResult.getRecords();
        if (records.isEmpty()) {
            return new Page<>();
        }

        Page<E> pager = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal(), pageResult.isSearchCount());
        List<E> list = new ArrayList<>(records.size());
        records.forEach(map -> list.add(BeanUtil.mapToBean(map, pageType, true, CopyOptions.create())));

        pager.setRecords(list);
        return pager;
    }

    /**
     * 统计数量
     *
     * @return 返回统计的数量，没有为0
     */
    public int count() {
        return executeCheck().count(this);
    }

    /**
     * 两个字段相等的条件
     * "column_name_1 = column_name_2"
     *
     * @param left  左边字段
     * @param right 右边字段
     * @param <L>   左边字段的类型
     * @param <R>   右边字段的类型
     * @return 返回本实例
     */
    public <L extends Model<L>, R extends Model<R>> QueryJoin<M> eq(SFunction<L, ?> left, SFunction<R, ?> right) {
        ColumnInfo<L> ciLeft = ColumnInfo.init(this.tableMap, left);
        ColumnInfo<R> ciRight = ColumnInfo.init(this.tableMap, right);
        apply(ciLeft
                .cndColumnStr()
                .concat(StrUtil.SPACE)
                .concat(StrUtil.EQ)
                .concat(StrUtil.SPACE)
                .concat(ciRight.cndColumnStr()));
        return typedThis;
    }

    /**
     * 批量创建相等的条件
     * 使用时，字段前需要带表别名，否则会出错
     *
     * @param params 相等的条件和参数
     * @param <V>    值类型
     * @return 返回本实例
     * @see QueryJoin#allEqFun(Map)
     */
    @Override
    public <V> QueryJoin<M> allEq(Map<String, V> params) {
        return this.allEq(params, true);
    }

    /**
     * 批量创建相等的条件
     * 使用时，字段前需要带表别名，否则会出错
     *
     * @param params      相等的条件和参数
     * @param null2IsNull 如果字段对应的值为null，则判断此字段为NULL
     * @param <V>         值类型
     * @return 返回本实例
     * @see QueryJoin#allEqFun(Map, boolean)
     */
    @Override
    public <V> QueryJoin<M> allEq(Map<String, V> params, boolean null2IsNull) {
        return super.allEq(params, null2IsNull);
    }

    /**
     * 批量创建相等的条件
     * 使用时，字段前需要带表别名，否则会出错
     *
     * @param filter 判断Map字段和值的条件，判断通过，则允许此字段参与条件
     * @param params 相等的条件和参数
     * @param <V>    值类型
     * @return 返回本实例
     * @see QueryJoin#allEqFun(BiPredicate, Map)
     */
    @Override
    public <V> QueryJoin<M> allEq(BiPredicate<String, V> filter, Map<String, V> params) {
        return this.allEq(filter, params, true);
    }

    /**
     * 批量创建相等的条件
     * 使用时，字段前需要带表别名，否则会出错
     *
     * @param filter      判断Map字段和值的条件，判断通过，则允许此字段参与条件
     * @param params      相等的条件和参数
     * @param null2IsNull 如果字段对应的值为null，则判断此字段为NULL
     * @param <V>         值类型
     * @return 返回本实例
     * @see QueryJoin#allEqFun(BiPredicate, Map, boolean)
     */
    @Override
    public <V> QueryJoin<M> allEq(BiPredicate<String, V> filter, Map<String, V> params, boolean null2IsNull) {
        return super.allEq(filter, params, null2IsNull);
    }

    /**
     * 批量创建相等的条件
     * 使用时，字段前需要带表别名，否则会出错
     *
     * @param params 相等的条件和参数
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> allEqFun(Map<SFunction<S, ?>, ?> params) {
        return this.allEqFun(params, true);
    }

    /**
     * 批量创建相等的条件
     * 使用时，字段前需要带表别名，否则会出错
     *
     * @param params      相等的条件和参数
     * @param null2IsNull 如果字段对应的值为null，则判断此字段为NULL
     * @param <S>         字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> allEqFun(Map<SFunction<S, ?>, ?> params, boolean null2IsNull) {
        if (params == null || params.isEmpty()) {
            return this;
        }

        Map<String, Object> maps = new HashMap<>(params.size());
        for (Map.Entry<SFunction<S, ?>, ?> entry : params.entrySet()) {
            SFunction<S, ?> func = entry.getKey();
            ColumnInfo<S> ci = ColumnInfo.init(tableMap, func);
            maps.put(ci.getJoinTableInfo().getAliasName().concat(StrUtil.DOT).concat(ci.getColumnName()), entry.getValue());
        }
        return this.allEq(maps, null2IsNull);
    }

    /**
     * 批量创建相等的条件
     * 使用时，字段前需要带表别名，否则会出错
     *
     * @param filter 判断Map字段和值的条件，判断通过，则允许此字段参与条件
     * @param params 相等的条件和参数
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <V, S extends Model<S>> QueryJoin<M> allEqFun(BiPredicate<String, V> filter, Map<SFunction<S, ?>, V> params) {
        return this.allEqFun(filter, params, true);
    }

    /**
     * 批量创建相等的条件
     * 使用时，字段前需要带表别名，否则会出错
     *
     * @param filter      判断Map字段和值的条件，判断通过，则允许此字段参与条件
     * @param params      相等的条件和参数
     * @param null2IsNull 如果字段对应的值为null，则判断此字段为NULL
     * @param <S>         字段类型
     * @return 返回本实例
     */
    public <V, S extends Model<S>> QueryJoin<M> allEqFun(BiPredicate<String, V> filter, Map<SFunction<S, ?>, V> params, boolean null2IsNull) {
        if (params == null || params.isEmpty()) {
            return this;
        }

        if (filter == null) {
            filter = (v1, v2) -> false;
        }

        Map<String, V> maps = new HashMap<>(params.size());
        for (Map.Entry<SFunction<S, ?>, V> entry : params.entrySet()) {
            SFunction<S, ?> func = entry.getKey();
            ColumnInfo<S> ci = ColumnInfo.init(tableMap, func);
            maps.put(ci.getJoinTableInfo().getAliasName().concat(StrUtil.DOT).concat(ci.getColumnName()), entry.getValue());
        }
        return this.allEq(filter, maps, null2IsNull);
    }

    /**
     * 添加等于条件，注意字段需要加表别名
     *
     * @param column 字段
     * @param val    值
     * @return 返回本实例
     * @see QueryJoin#eq(SFunction, Object)
     */
    @Override
    public QueryJoin<M> eq(String column, Object val) {
        return super.eq(column, val);
    }

    /**
     * 指定实体类字段添加等于条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> eq(SFunction<S, ?> column, Object val) {
        return this.eq(true, column, val);
    }

    /**
     * 指定实体类字段添加等于条件
     *
     * @param cnd    条件
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> eq(boolean cnd, SFunction<S, ?> column, Object val) {
        return this.eq(cnd, ColumnInfo.init(tableMap, column).cndColumnStr(), val);
    }

    /**
     * 当值不为空时拼接条件
     * 指定实体类字段添加等于条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> eqIfNotEmpty(SFunction<S, ?> column, Object val) {
        return this.eq(ObjectUtils.isNotEmpty(val), column, val);
    }

    /**
     * 添加不等于条件，注意字段需要加表别名
     *
     * @param column 字段
     * @param val    值
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> ne(String column, Object val) {
        return super.ne(column, val);
    }

    /**
     * 指定实体类字段添加不等于条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> ne(SFunction<S, ?> column, Object val) {
        return this.ne(true, column, val);
    }

    /**
     * 指定实体类字段添加不等于条件
     *
     * @param cnd    条件
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> ne(boolean cnd, SFunction<S, ?> column, Object val) {
        return this.ne(cnd, ColumnInfo.init(tableMap, column).cndColumnStr(), val);
    }

    /**
     * 当值不为空时拼接条件
     * 指定实体类字段添加不等于条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> neIfNotEmpty(SFunction<S, ?> column, Object val) {
        return this.ne(ObjectUtils.isNotEmpty(val), column, val);
    }

    /**
     * 添加大于条件，注意字段需要加表别名
     *
     * @param column 字段
     * @param val    值
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> gt(String column, Object val) {
        return super.gt(column, val);
    }

    /**
     * 指定实体类字段添加大于条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> gt(SFunction<S, ?> column, Object val) {
        return this.gt(true, column, val);
    }

    /**
     * 指定实体类字段添加大于条件
     *
     * @param cnd    条件
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> gt(boolean cnd, SFunction<S, ?> column, Object val) {
        return this.gt(cnd, ColumnInfo.init(tableMap, column).cndColumnStr(), val);
    }

    /**
     * 当值不为空时拼接条件
     * 指定实体类字段添加大于条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> gtIfNotEmpty(SFunction<S, ?> column, Object val) {
        return this.gt(ObjectUtils.isNotEmpty(val), column, val);
    }

    /**
     * 添加大于等于条件，注意字段需要加表别名
     *
     * @param column 字段
     * @param val    值
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> ge(String column, Object val) {
        return super.ge(column, val);
    }

    /**
     * 指定实体类字段添加大于等于条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> ge(SFunction<S, ?> column, Object val) {
        return this.ge(true, column, val);
    }

    /**
     * 指定实体类字段添加大于等于条件
     *
     * @param cnd    条件
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> ge(boolean cnd, SFunction<S, ?> column, Object val) {
        return this.ge(cnd, ColumnInfo.init(tableMap, column).cndColumnStr(), val);
    }

    /**
     * 当值不为空时拼接条件
     * 指定实体类字段添加大于等于条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> geIfNotEmpty(SFunction<S, ?> column, Object val) {
        return this.ge(ObjectUtils.isNotEmpty(val), column, val);
    }

    /**
     * 添加小于条件，注意字段需要加表别名
     *
     * @param column 字段
     * @param val    值
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> lt(String column, Object val) {
        return super.lt(column, val);
    }

    /**
     * 指定实体类字段添加小于条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> lt(SFunction<S, ?> column, Object val) {
        return this.lt(true, column, val);
    }

    /**
     * 指定实体类字段添加小于条件
     *
     * @param cnd    条件
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> lt(boolean cnd, SFunction<S, ?> column, Object val) {
        return this.lt(cnd, ColumnInfo.init(tableMap, column).cndColumnStr(), val);
    }

    /**
     * 当值不为空时拼接条件
     * 指定实体类字段添加小于条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> ltIfNotEmpty(SFunction<S, ?> column, Object val) {
        return this.lt(ObjectUtils.isNotEmpty(val), column, val);
    }

    /**
     * 添加小于等于条件，注意字段需要加表别名
     *
     * @param column 字段
     * @param val    值
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> le(String column, Object val) {
        return super.le(column, val);
    }

    /**
     * 指定实体类字段添加小于等于条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> le(SFunction<S, ?> column, Object val) {
        return this.le(true, column, val);
    }

    /**
     * 指定实体类字段添加小于等于条件
     *
     * @param cnd    条件
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> le(boolean cnd, SFunction<S, ?> column, Object val) {
        return this.le(cnd, ColumnInfo.init(tableMap, column).cndColumnStr(), val);
    }

    /**
     * 当值不为空时拼接条件
     * 指定实体类字段添加小于等于条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> leIfNotEmpty(SFunction<S, ?> column, Object val) {
        return this.le(ObjectUtils.isNotEmpty(val), column, val);
    }

    /**
     * 添加 BETWEEN AND 条件，注意字段需要加表别名
     *
     * @param column 字段
     * @param val1   第一个值
     * @param val2   第二个值
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> between(String column, Object val1, Object val2) {
        return super.between(column, val1, val2);
    }

    /**
     * 添加 BETWEEN AND 条件
     *
     * @param column 字段
     * @param val1   第一个值
     * @param val2   第二个值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> between(SFunction<S, ?> column, Object val1, Object val2) {
        return this.between(true, column, val1, val2);
    }

    /**
     * 添加 BETWEEN AND 条件
     *
     * @param cnd    判断条件
     * @param column 字段
     * @param val1   第一个值
     * @param val2   第二个值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> between(boolean cnd, SFunction<S, ?> column, Object val1, Object val2) {
        return this.between(cnd, ColumnInfo.init(tableMap, column).cndColumnStr(), val1, val2);
    }

    /**
     * 添加 NOT BETWEEN AND 条件，注意字段需要加表别名
     *
     * @param column 字段
     * @param val1   第一个值
     * @param val2   第二个值
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> notBetween(String column, Object val1, Object val2) {
        return super.notBetween(column, val1, val2);
    }

    /**
     * 添加 NOT BETWEEN AND 条件
     *
     * @param column 字段
     * @param val1   第一个值
     * @param val2   第二个值
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> notBetween(SFunction<S, ?> column, Object val1, Object val2) {
        return this.notBetween(true, column, val1, val2);
    }

    /**
     * 添加 NOT BETWEEN AND 条件
     *
     * @param column 字段
     * @param val1   第一个值
     * @param val2   第二个值
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> notBetween(boolean cnd, SFunction<S, ?> column, Object val1, Object val2) {
        return super.notBetween(cnd, ColumnInfo.init(tableMap, column).cndColumnStr(), val1, val2);
    }

    /**
     * 添加 LIKE 条件，注意字段需要加表别名
     *
     * @param column 字段
     * @param val    值
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> like(String column, Object val) {
        return super.like(column, val);
    }

    /**
     * 指定实体类字段添加 LIKE 条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> like(SFunction<S, ?> column, Object val) {
        return this.like(ColumnInfo.init(tableMap, column).cndColumnStr(), val);
    }

    /**
     * 指定实体类字段添加 LIKE 条件
     *
     * @param cnd    条件
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> like(boolean cnd, SFunction<S, ?> column, Object val) {
        return this.le(cnd, ColumnInfo.init(tableMap, column).cndColumnStr(), val);
    }

    /**
     * 当值不为空时拼接条件
     * 指定实体类字段添加 LIKE 条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> likeIfNotEmpty(SFunction<S, ?> column, Object val) {
        return this.like(ObjectUtils.isNotEmpty(val), ColumnInfo.init(tableMap, column).cndColumnStr(), val);
    }

    /**
     * 添加 NOT LIKE 条件，注意字段需要加表别名
     *
     * @param column 字段
     * @param val    值
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> notLike(String column, Object val) {
        return super.notLike(column, val);
    }

    /**
     * 指定实体类字段添加 NOT LIKE 条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> notLike(SFunction<S, ?> column, Object val) {
        return this.notLike(true, column, val);
    }

    /**
     * 指定实体类字段添加 NOT LIKE 条件
     *
     * @param cnd    条件
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> notLike(boolean cnd, SFunction<S, ?> column, Object val) {
        return this.notLike(cnd, ColumnInfo.init(tableMap, column).cndColumnStr(), val);
    }

    /**
     * 当值不为空时拼接条件
     * 指定实体类字段添加 NOT LIKE 条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> notLikeIfNotEmpty(SFunction<S, ?> column, Object val) {
        return this.notLike(ObjectUtils.isNotEmpty(val), column, val);
    }

    /**
     * 添加左 LIKE 条件，注意字段需要加表别名
     *
     * @param column 字段
     * @param val    值
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> likeLeft(String column, Object val) {
        return super.likeLeft(column, val);
    }

    /**
     * 指定实体类字段添加左 LIKE 条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> likeLeft(SFunction<S, ?> column, Object val) {
        return this.likeLeft(true, column, val);
    }

    /**
     * 指定实体类字段添加左 LIKE 条件
     *
     * @param cnd    条件
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> likeLeft(boolean cnd, SFunction<S, ?> column, Object val) {
        return this.likeLeft(cnd, ColumnInfo.init(tableMap, column).cndColumnStr(), val);
    }

    /**
     * 当值不为空时拼接条件
     * 指定实体类字段添加左 LIKE 条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> likeLeftIfNotEmpty(SFunction<S, ?> column, Object val) {
        return this.likeLeft(ObjectUtils.isNotEmpty(val), column, val);
    }

    /**
     * 添加右 LIKE 条件，注意字段需要加表别名
     *
     * @param column 字段
     * @param val    值
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> likeRight(String column, Object val) {
        return super.likeRight(column, val);
    }

    /**
     * 指定实体类字段添加右 LIKE 条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> likeRight(SFunction<S, ?> column, Object val) {
        return this.likeRight(true, column, val);
    }

    /**
     * 指定实体类字段添加右 LIKE 条件
     *
     * @param cnd    条件
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> likeRight(boolean cnd, SFunction<S, ?> column, Object val) {
        return this.likeRight(cnd, ColumnInfo.init(tableMap, column).cndColumnStr(), val);
    }

    /**
     * 当值不为空时拼接条件
     * 指定实体类字段添加右 LIKE 条件
     *
     * @param column 实体类字段
     * @param val    值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> likeRightIfNotEmpty(SFunction<S, ?> column, Object val) {
        return this.likeRight(ObjectUtils.isNotEmpty(val), column, val);
    }

    /**
     * 添加字段为空的条件，注意字段需要加表别名
     *
     * @param column 字段
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> isNull(String column) {
        return super.isNull(column);
    }

    /**
     * 添加字段为空的条件
     *
     * @param column 字段
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> isNull(SFunction<S, ?> column) {
        return this.isNull(true, column);
    }

    /**
     * 添加字段为空的条件
     *
     * @param cnd    判断条件
     * @param column 字段
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> isNull(boolean cnd, SFunction<S, ?> column) {
        return this.isNull(cnd, ColumnInfo.init(tableMap, column).cndColumnStr());
    }

    /**
     * 添加字段不为空的条件，注意字段需要加表别名
     *
     * @param column 字段
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> isNotNull(String column) {
        return super.isNotNull(column);
    }

    /**
     * 添加字段不为空的条件
     *
     * @param column 字段
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> isNotNull(SFunction<S, ?> column) {
        return this.isNotNull(true, column);
    }

    /**
     * 添加字段不为空的条件
     *
     * @param cnd    判断条件
     * @param column 字段
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> isNotNull(boolean cnd, SFunction<S, ?> column) {
        return this.isNotNull(cnd, ColumnInfo.init(tableMap, column).cndColumnStr());
    }

    /**
     * 添加 IN 条件，注意字段需要添加表别名
     *
     * @param column 字段
     * @param coll   集合
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> in(String column, Collection<?> coll) {
        return this.in(true, column, coll);
    }

    /**
     * 添加 IN 条件，注意字段需要添加表别名
     *
     * @param condition 判断条件
     * @param column    字段
     * @param coll      集合
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> in(boolean condition, String column, Collection<?> coll) {
        if (coll != null && coll.size() == 1) {
            return super.eq(condition, column, CollUtil.get(coll, 0));
        }
        return super.in(condition, column, coll);
    }

    /**
     * 添加 IN 条件
     *
     * @param column 字段
     * @param coll   集合
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> in(SFunction<S, ?> column, Collection<?> coll) {
        return this.in(true, column, coll);
    }

    /**
     * 添加 IN 条件
     *
     * @param cnd    判断条件
     * @param column 字段
     * @param coll   集合
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> in(boolean cnd, SFunction<S, ?> column, Collection<?> coll) {
        return this.in(cnd, ColumnInfo.init(tableMap, column).cndColumnStr(), coll);
    }

    /**
     * 添加 IN 条件
     *
     * @param column 字段
     * @param coll   集合
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> inIfNotEmpty(SFunction<S, ?> column, Collection<?> coll) {
        return this.in(CollectionUtils.isNotEmpty(coll), column, coll);
    }

    /**
     * 添加 IN 条件，注意字段需要添加表别名
     *
     * @param column 字段
     * @param values 多个值
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> in(String column, Object... values) {
        return this.in(true, column, values);
    }

    /**
     * 添加 IN 条件，注意字段需要添加表别名
     *
     * @param condition 判断条件
     * @param column    字段
     * @param values    多个值
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> in(boolean condition, String column, Object... values) {
        return this.in(condition, column, Arrays.asList(values));
    }

    /**
     * 添加 IN 条件
     *
     * @param column 字段
     * @param values 多个值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> in(SFunction<S, ?> column, Object... values) {
        return this.in(true, column, values);
    }

    /**
     * 添加 IN 条件
     *
     * @param cnd    判断条件
     * @param column 字段
     * @param values 多个值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> in(boolean cnd, SFunction<S, ?> column, Object... values) {
        return this.in(cnd, column, Arrays.asList(values));
    }

    /**
     * 添加 IN 条件
     *
     * @param column 字段
     * @param values 多个值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> inIfNotEmpty(SFunction<S, ?> column, Object... values) {
        return this.in(ArrayUtils.isNotEmpty(values), column, values);
    }

    /**
     * 添加 NOT IN 条件，注意字段需要添加表别名
     *
     * @param column 字段
     * @param coll   集合
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> notIn(String column, Collection<?> coll) {
        return this.notIn(true, column, coll);
    }

    @Override
    public QueryJoin<M> notIn(boolean condition, String column, Collection<?> coll) {
        if (coll != null && coll.size() == 1) {
            this.ne(condition, column, CollUtil.get(coll, 0));
        }
        return super.notIn(condition, column, coll);
    }

    /**
     * 添加 NOT IN 条件
     *
     * @param column 字段
     * @param coll   集合
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> notIn(SFunction<S, ?> column, Collection<?> coll) {
        return this.notIn(true, column, coll);
    }

    /**
     * 添加 NOT IN 条件
     * <p>
     * 如果传入的集合元素数量为1，则转成不等于的条件
     *
     * @param cnd    判断条件
     * @param column 字段
     * @param coll   集合
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> notIn(boolean cnd, SFunction<S, ?> column, Collection<?> coll) {
        return this.notIn(cnd, ColumnInfo.init(tableMap, column).cndColumnStr(), coll);
    }

    /**
     * 添加 NOT IN 条件
     *
     * @param column 字段
     * @param coll   集合
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> notInIfNotEmpty(SFunction<S, ?> column, Collection<?> coll) {
        return this.notIn(CollectionUtils.isNotEmpty(coll), column, coll);
    }

    /**
     * 添加 NOT IN 条件，注意字段需要添加表别名
     *
     * @param column 字段
     * @param values 多个值
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> notIn(String column, Object... values) {
        return this.notIn(true, column, values);
    }

    /**
     * 添加 NOT IN 条件，注意字段需要添加表别名
     *
     * @param condition 判断条件
     * @param column    字段
     * @param values    多个值
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> notIn(boolean condition, String column, Object... values) {
        return this.notIn(condition, column, Arrays.asList(values));
    }

    /**
     * 添加 NOT IN 条件
     *
     * @param column 字段
     * @param values 多个值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> notIn(SFunction<S, ?> column, Object... values) {
        return this.notIn(true, column, values);
    }

    /**
     * 添加 NOT IN 条件
     *
     * @param cnd    判断条件
     * @param column 字段
     * @param values 多个值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> notIn(boolean cnd, SFunction<S, ?> column, Object... values) {
        return this.notIn(cnd, column, Arrays.asList(values));
    }

    /**
     * 添加 NOT IN 条件
     *
     * @param column 字段
     * @param values 多个值
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> notInIfNotEmpty(SFunction<S, ?> column, Object... values) {
        return this.notIn(ArrayUtils.isNotEmpty(values), column, values);
    }

    /**
     * 添加 IN SQL语句，
     * ex1：id IN (SELECT id FROM table)
     * ex2：id IN ("1,2,3,4,5")
     *
     * @param column  字段，注意需要添加表别名
     * @param inValue 值或SQL语句
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> inSql(String column, String inValue) {
        return super.inSql(column, inValue);
    }

    /**
     * 添加 IN SQL语句，注意需要添加表别名
     * ex1：id IN (SELECT id FROM table)
     * ex2：id IN ("1,2,3,4,5")
     *
     * @param column  字段
     * @param inValue 值或SQL语句
     * @param <S>     字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> inSql(SFunction<S, ?> column, String inValue) {
        return this.inSql(true, column, inValue);
    }

    /**
     * 添加 IN SQL语句，注意需要添加表别名
     * ex1：id IN (SELECT id FROM table)
     * ex2：id IN (1,2,3,4,5)
     *
     * @param cnd     判断条件
     * @param column  字段
     * @param inValue 值或SQL语句
     * @param <S>     字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> inSql(boolean cnd, SFunction<S, ?> column, String inValue) {
        return this.inSql(cnd, ColumnInfo.init(tableMap, column).cndColumnStr(), inValue);
    }

    /**
     * 添加 NOT IN SQL语句，注意需要添加表别名
     * ex1：id NOT IN (SELECT id FROM table)
     * ex2：id NOT IN (1,2,3,4,5)
     *
     * @param column  字段
     * @param inValue 值或SQL语句
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> notInSql(String column, String inValue) {
        return super.notInSql(column, inValue);
    }

    /**
     * 添加 NOT IN SQL语句
     * ex1：id NOT IN (SELECT id FROM table)
     * ex2：id NOT IN (1,2,3,4,5)
     *
     * @param column  字段
     * @param inValue 值或SQL语句
     * @param <S>     字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> notInSql(SFunction<S, ?> column, String inValue) {
        return this.notInSql(true, column, inValue);
    }

    /**
     * 添加 NOT IN SQL语句
     * ex1：id NOT IN (SELECT id FROM table)
     * ex2：id NOT IN (1,2,3,4,5)
     *
     * @param cnd     判断条件
     * @param column  字段
     * @param inValue 值或SQL语句
     * @param <S>     字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> notInSql(boolean cnd, SFunction<S, ?> column, String inValue) {
        return this.notInSql(cnd, ColumnInfo.init(tableMap, column).cndColumnStr(), inValue);
    }

    /**
     * 按照字段分组，注意字段需要加表别名
     *
     * @param column 分组的字段
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> groupBy(String column) {
        return super.groupBy(column);
    }

    /**
     * 按照字段分组
     *
     * @param column 分组的字段
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> groupBy(SFunction<S, ?> column) {
        return this.groupBy(true, column);
    }

    /**
     * 按照字段分组
     *
     * @param cnd    判断条件
     * @param column 分组的字段
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> groupBy(boolean cnd, SFunction<S, ?> column) {
        return this.groupBy(cnd, ColumnInfo.init(tableMap, column).cndColumnStr());
    }

    /**
     * 按照字段分组，注意字段需要加表别名
     *
     * @param columns 多个分组的字段
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> groupBy(String... columns) {
        return super.groupBy(columns);
    }

    /**
     * 按照多个字段分组
     *
     * @param columns 分组的字段
     * @param <S>     字段类型
     * @return 返回本实例
     */
    @SafeVarargs
    public final <S extends Model<S>> QueryJoin<M> groupBy(SFunction<S, ?>... columns) {
        return this.groupBy(true, columns);
    }

    /**
     * 按照多个字段分组
     *
     * @param cnd     判断条件
     * @param columns 分组的字段
     * @param <S>     字段类型
     * @return 返回本实例
     */
    @SafeVarargs
    public final <S extends Model<S>> QueryJoin<M> groupBy(boolean cnd, SFunction<S, ?>... columns) {
        return this.groupBy(cnd, transToStr(columns));
    }

    /**
     * 按照字段正序排序，注意字段需要加表别名
     *
     * @param column 排序的字段
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> orderByAsc(String column) {
        return super.orderByAsc(column);
    }

    /**
     * 按照字段正序排序
     *
     * @param column 排序的字段
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> orderByAsc(SFunction<S, ?> column) {
        return this.orderByAsc(true, column);
    }

    /**
     * 按照字段正序排序
     *
     * @param cnd    判断条件
     * @param column 排序的字段
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> orderByAsc(boolean cnd, SFunction<S, ?> column) {
        return super.orderByAsc(cnd, ColumnInfo.init(tableMap, column).cndColumnStr());
    }

    /**
     * 按照多个字段正序排序，注意字段需要加表别名
     *
     * @param columns 多个排序的字段
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> orderByAsc(String... columns) {
        return super.orderByAsc(columns);
    }

    /**
     * 按照多个字段正序排序，注意字段需要加表别名
     *
     * @param columns 多个排序的字段
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> orderByAsc(boolean condition, String... columns) {
        return super.orderByAsc(condition, columns);
    }

    /**
     * 按照字段正序排序
     *
     * @param columns 多个排序的字段
     * @param <S>     字段类型
     * @return 返回本实例
     */
    @SafeVarargs
    public final <S extends Model<S>> QueryJoin<M> orderByAsc(SFunction<S, ?>... columns) {
        return this.orderByAsc(true, columns);
    }

    /**
     * 按照字段正序排序
     *
     * @param cnd     判断条件
     * @param columns 多个排序的字段
     * @param <S>     字段类型
     * @return 返回本实例
     */
    @SafeVarargs
    public final <S extends Model<S>> QueryJoin<M> orderByAsc(boolean cnd, SFunction<S, ?>... columns) {
        return this.orderByAsc(cnd, transToStr(columns));
    }

    /**
     * 按照字段倒序排序，注意字段需要加表别名
     *
     * @param column 排序的字段
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> orderByDesc(String column) {
        return super.orderByDesc(column);
    }

    /**
     * 按照字段倒序排序
     *
     * @param column 排序的字段
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> orderByDesc(SFunction<S, ?> column) {
        return this.orderByDesc(true, column);
    }

    /**
     * 按照字段倒序排序
     *
     * @param cnd    判断条件
     * @param column 排序的字段
     * @param <S>    字段类型
     * @return 返回本实例
     */
    public <S extends Model<S>> QueryJoin<M> orderByDesc(boolean cnd, SFunction<S, ?> column) {
        return super.orderByDesc(cnd, ColumnInfo.init(tableMap, column).cndColumnStr());
    }

    /**
     * 按照多个字段倒序排序，注意字段需要加表别名
     *
     * @param columns 多个排序的字段
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> orderByDesc(String... columns) {
        return super.orderByDesc(columns);
    }

    /**
     * 按照多个字段倒序排序，注意字段需要加表别名
     *
     * @param condition 判断条件
     * @param columns   多个排序的字段
     * @return 返回本实例
     */
    @Override
    public QueryJoin<M> orderByDesc(boolean condition, String... columns) {
        return super.orderByDesc(condition, columns);
    }

    /**
     * 按照字段倒序排序
     *
     * @param columns 多个排序的字段
     * @param <S>     字段类型
     * @return 返回本实例
     */
    @SafeVarargs
    public final <S extends Model<S>> QueryJoin<M> orderByDesc(SFunction<S, ?>... columns) {
        return this.orderByDesc(true, columns);
    }

    /**
     * 按照字段倒序排序
     *
     * @param cnd     判断条件
     * @param columns 多个排序的字段
     * @param <S>     字段类型
     * @return 返回本实例
     */
    @SafeVarargs
    public final <S extends Model<S>> QueryJoin<M> orderByDesc(boolean cnd, SFunction<S, ?>... columns) {
        return this.orderByDesc(cnd, transToStr(columns));
    }

    @Override
    public QueryJoin<M> select(Predicate<TableFieldInfo> predicate) {
        return select(null, predicate);
    }

    /**
     * 将多个函数字段转换成字符串字段
     *
     * @param columns 多个字段
     * @param <S>     字段类型
     * @return 返回本实例
     */
    @SafeVarargs
    private final <S extends Model<S>> String[] transToStr(SFunction<S, ?>... columns) {
        if (ArrayUtils.isNotEmpty(columns)) {
            String[] columnsStr = new String[columns.length];
            for (int i = 0; i < columns.length; i++) {
                columnsStr[i] = ColumnInfo.init(tableMap, columns[i]).cndColumnStr();
            }
            return columnsStr;
        }
        return new String[0];
    }
}
