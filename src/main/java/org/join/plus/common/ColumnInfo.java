package org.join.plus.common;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.core.toolkit.support.SerializedLambda;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Getter;

import java.util.Map;

/**
 * 解析字段信息
 *
 * @author suyun
 * @date 2021-07-28 14:20
 */
public class ColumnInfo<CI extends Model<CI>> {
    @Getter
    private final SFunction<CI, ?> func;
    @Getter
    private final JoinTableInfo joinTableInfo;
    @Getter
    private final String columnName;
    @Getter
    private final String columnAlias;

    /**
     * 通过构造器生成属性信息实例
     *
     * @param tableMap 已经缓存的关联表Map
     * @param func     字段属性
     * @param alias    字段别名
     */
    private ColumnInfo(Map<String, JoinTableInfo> tableMap, SFunction<CI, ?> func, String alias) {
        if (tableMap == null || tableMap.isEmpty()) {
            throw new MybatisPlusException("获取字段信息错误，没有表信息的缓存");
        }

        this.func = func;
        SerializedLambda sl = LambdaUtils.resolve(func);
        Class<?> cla = sl.getInstantiatedType();
        String fieldName = StrUtil.getGeneralField(sl.getImplMethodName());
        this.joinTableInfo = tableMap.get(cla.getName());
        if (this.joinTableInfo == null) {
            throw new MybatisPlusException(String.format("所查询的字段[%s]所属的表实体[%s]尚未加入关联查询", fieldName, cla.getName()));
        }

        String ca;
        if (this.joinTableInfo.getTableInfo().getKeyProperty().equals(fieldName)) {
            this.columnName = this.joinTableInfo.getTableInfo().getKeyColumn();
            ca = this.joinTableInfo.getTableInfo().getKeyProperty();
        } else {

            TableFieldInfo tfi = this.joinTableInfo
                    .getTableInfo()
                    .getFieldList()
                    .stream()
                    .filter(f -> f.getProperty().equals(fieldName))
                    .findFirst()
                    .orElse(null);
            if (tfi == null) {
                throw new MybatisPlusException(String.format("所查询的属性[%s]对应的字段不存在", fieldName));
            }

            this.columnName = tfi.getColumn();
            ca = tfi.getProperty();
        }

        if (StrUtil.isBlank(alias)) {
            this.columnAlias = ca;
        } else {
            this.columnAlias = alias;
        }
    }

    /**
     * 获取 "aliasTableName.column_name"
     *
     * @return 返回
     */
    public String cndColumnStr() {
        return this.joinTableInfo
                .getAliasName()
                .concat(StrUtil.DOT)
                .concat(this.columnName);
    }

    /**
     * 获取查询字段 "aliasTableName.column_name AS columnAlias"
     *
     * @return 返回
     */
    public String selectColumnStr() {
        return this.cndColumnStr()
                .concat(StrUtil.SPACE)
                .concat(StrUtil.AS)
                .concat(StrUtil.SPACE)
                .concat(this.columnAlias);
    }

    /**
     * 初始化获取字段属性实例
     *
     * @param tableMap 缓存
     * @param func     字段属性
     * @param <I>      字段属性所属的实体类型
     * @return 返回字段属性
     */
    public static <I extends Model<I>> ColumnInfo<I> init(Map<String, JoinTableInfo> tableMap, SFunction<I, ?> func) {
        return new ColumnInfo<>(tableMap, func, null);
    }

    /**
     * 初始化获取字段属性实例
     *
     * @param tableMap 缓存
     * @param func     字段属性
     * @param <I>      字段属性所属的实体类型
     * @return 返回字段属性
     */
    public static <I extends Model<I>> ColumnInfo<I> init(Map<String, JoinTableInfo> tableMap, SFunction<I, ?> func, String alias) {
        return new ColumnInfo<>(tableMap, func, alias);
    }
}
