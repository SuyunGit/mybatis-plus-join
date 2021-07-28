package org.join.plus.common;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.toolkit.ArrayUtils;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.core.toolkit.support.SerializedLambda;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Getter;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * @author suyun
 * @date 2021-07-16 18:07
 */
@Getter
public class JoinTableInfo implements Serializable {
    private final static long serialVersionUID = 1L;

    /**
     * 表信息
     */
    private final TableInfo tableInfo;

    private final boolean isMaster;

    /**
     * 简单类名
     */
    private final String entityName;

    /**
     * 表别名，从注解 TableAlias 中取值
     * 没有则使用类名
     */
    private final String aliasName;

    /**
     * 存储指定查询的字段
     */
    private final Map<String, String> selectedColumns = CollectionUtils.newHashMap();

    /**
     * 查询类型
     */
    private SelectType selectType;

    public JoinTableInfo(TableInfo tableInfo) {
        this(tableInfo, false, SelectType.NONE);
    }

    public JoinTableInfo(TableInfo tableInfo, SelectType selectType) {
        this(tableInfo, false, selectType);
    }

    public JoinTableInfo(TableInfo tableInfo, boolean isMaster, SelectType selectType) {
        this(tableInfo, null, null, isMaster, selectType);
    }

    public JoinTableInfo(TableInfo tableInfo, String entityName, String aliasName, boolean isMaster, SelectType selectType) {
        Assert.notNull(tableInfo, "表信息缺失");
        this.tableInfo = tableInfo;
        this.isMaster = isMaster;

        if (StrUtil.isBlank(entityName)) {
            this.entityName = StrUtil.lowerFirst(tableInfo.getEntityType().getSimpleName());
        } else {
            this.entityName = entityName;
        }

        if (StrUtil.isBlank(aliasName)) {
            this.aliasName = this.entityName;
        } else {
            this.aliasName = aliasName;
        }
        this.selectType = selectType;

        switch (selectType) {
            case ALL:
                this.selectAll();
                break;
            case NONE:
                this.selectNone();
                break;
            case SOME:
                this.selectSome();
                break;
            default:
                throw new MybatisPlusException("不支持的查询类型:" + selectType);
        }
    }

    /**
     * 查询全部字段
     */
    public void selectAll() {
        this.selectType = SelectType.ALL;
        this.tableInfo.getFieldList()
                .forEach(this::selectColumn);
        this.selectColumn(this.tableInfo.getKeyColumn(), this.tableInfo.getKeyProperty());
    }

    /**
     * 不查询任何字段
     */
    public void selectNone() {
        this.selectType = SelectType.NONE;
        this.selectedColumns.clear();
    }

    /**
     * 追加指定一些字段进行查询
     * 1.如果之前是查询全部字段的，则会先清空字段
     * 2.如果之前是查询空或指定查询，则进行追加查询
     */
    @SafeVarargs
    public final <C extends Model<C>> void selectSome(SFunction<C, ?>... cols) {
        if (this.selectType == SelectType.ALL) {
            this.selectNone();
        }

        this.selectType = SelectType.SOME;
        if (ArrayUtils.isEmpty(cols)) {
            return;
        }

        Arrays.stream(cols)
                .forEach(c -> {
                    SerializedLambda sl = LambdaUtils.resolve(c);
                    String fieldName = StrUtil.getGeneralField(sl.getImplMethodName());
                    TableFieldInfo tableFieldInfo = this.tableInfo.getFieldList()
                            .stream()
                            .filter(f -> f.getProperty().equals(fieldName))
                            .findFirst()
                            .orElse(null);
                    if (tableFieldInfo == null) {
                        throw new MybatisPlusException(StrUtil.format("在实体[{}]中未找到查询的字段[{}]",
                                tableInfo.getEntityType().getName(), fieldName));
                    }

                    this.selectColumn(tableFieldInfo);
                });
    }

    /**
     * 追加指定一些字段进行查询
     * 1.如果之前是查询全部字段的，则会先清空字段
     * 2.如果之前是查询空或指定查询，则进行追加查询
     */
    public void selectSome(String column, String aliasName) {
        if (this.selectType == SelectType.ALL) {
            this.selectNone();
        }

        this.selectType = SelectType.SOME;
        selectColumn(column, aliasName);
    }

    /**
     * 将指定的字段加入查询列表中
     *
     * @param tableFieldInfo 指定查询的字段
     */
    public void selectColumn(TableFieldInfo tableFieldInfo) {
        String columnName = tableFieldInfo.getColumn();
        String fieldName = tableFieldInfo.getProperty();
        this.selectColumn(columnName, fieldName);
    }

    /**
     * 将指定的字段加入查询列表中
     *
     * @param column    指定查询的字段，仅仅是字段名，不带表别名
     * @param aliasName 指定查询的字段别名
     */
    public void selectColumn(String column, String aliasName) {
        this.selectedColumns.put(aliasName, this.aliasName.concat(tableInfo.DOT).concat(column));
    }

    /**
     * 将选定的查询字段转换成sql，不包含任何sql关键字
     *
     * @return 返回
     */
    public String selectString() {
        if (this.selectedColumns.isEmpty()) {
            return StrUtil.EMPTY;
        }

        StringBuilder sql = new StringBuilder();
        this.selectedColumns.forEach((k, v) -> sql.append(v)
                .append(StrUtil.SPACE)
                .append(StrUtil.AS)
                .append(StrUtil.SPACE)
                .append(k)
                .append(StrUtil.COMMA));

        return sql.substring(0, sql.length() - 1);
    }

    /**
     * 获取逻辑删除的字段信息
     *
     * @return 返回，为空表示没有逻辑删除
     */
    public TableFieldInfo getLogicDeleteField() {
        if (!this.getTableInfo().isLogicDelete()) {
            return null;
        }

        return this.getTableInfo().getFieldList()
                .stream()
                .filter(TableFieldInfo::isLogicDelete)
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (this == o) return true;
        if (o instanceof TableInfo) {
            TableInfo tableInfo = (TableInfo) o;
            return this.equals(new JoinTableInfo(tableInfo, SelectType.NONE));
        }

        return false;
    }

    @Override
    public int hashCode() {
        // 表名、别名一致，表示重复
        return Objects.hash(aliasName, this.tableInfo.getTableName());
    }
}
