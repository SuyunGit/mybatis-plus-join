package org.join.plus.common;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import org.join.plus.query.QueryJoin;

/**
 * 关联
 *
 * @author suyun
 * @date 2021-07-28 14:22
 */
public class JoinOn<M extends Model<M>, J extends Model<J>> {
    private final JoinType joinType;
    private final StringBuilder fromSql;
    private final QueryJoin<M> queryJoin;

    public JoinOn(JoinType joinType, StringBuilder fromSql, QueryJoin<M> queryJoin) {
        this.joinType = joinType;
        this.fromSql = fromSql;
        this.queryJoin = queryJoin;
    }

    /**
     * 条件关联，此处只能作为关系关联，无法添加查询条件
     *
     * @param left  左属性，可任意选择已经添加关联的实体字段
     * @param right 右属性，只能选择此次关联实体的字段
     * @param <P>   左属性实体类型
     * @return 返回QueryJoin实例
     */
    public <P extends Model<P>> QueryJoin<M> on(SFunction<P, ?> left, SFunction<J, ?> right) {
        return on(left, null, right, null);
    }

    /**
     * 条件关联，此处只能作为关系关联，无法添加查询条件
     *
     * @param left  左属性，可任意选择已经添加关联的实体字段
     * @param right 右属性，只能选择此次关联实体的字段
     * @param <P>   左属性实体类型
     * @return 返回QueryJoin实例
     */
    public <P extends Model<P>> QueryJoin<M> on(SFunction<P, ?> left, String leftAs, SFunction<J, ?> right) {
        return on(left, leftAs, right, null);
    }

    /**
     * 条件关联，此处只能作为关系关联，无法添加查询条件
     *
     * @param left  左属性，可任意选择已经添加关联的实体字段
     * @param right 右属性，只能选择此次关联实体的字段
     * @param <P>   左属性实体类型
     * @return 返回QueryJoin实例
     */
    public <P extends Model<P>> QueryJoin<M> on(SFunction<P, ?> left, SFunction<J, ?> right, String rightAs) {
        return on(left, null, right, rightAs);
    }

    /**
     * 条件关联，此处只能作为关系关联，无法添加查询条件
     *
     * @param left  左属性，可任意选择已经添加关联的实体字段
     * @param right 右属性，只能选择此次关联实体的字段
     * @param <P>   左属性实体类型
     * @return 返回QueryJoin实例
     */
    public <P extends Model<P>> QueryJoin<M> on(SFunction<P, ?> left, String leftAs, SFunction<J, ?> right, String rightAs) {
        if (left == null && right == null) {
            return queryJoin;
        }

        if (this.fromSql.length() == 0) {
            throw new MybatisPlusException("Sql错误，From SQL为空");
        }

        if (this.joinType == JoinType.WHERE) {
            return queryJoin.eqAs(left, leftAs, right, rightAs);
        }

        ColumnInfo<P> ciRight = ColumnInfo.init(queryJoin.getQueryTables(), left, leftAs);
        ColumnInfo<J> ciLeft = ColumnInfo.init(queryJoin.getQueryTables(), right, rightAs);

        /// " ON leftTableAlias.column_name = rightTableAlias.column_name"
        this.fromSql
                .append(StrUtil.SPACE)
                .append(StrUtil.ON)
                .append(StrUtil.SPACE)
                .append(ciLeft.cndColumnStr())
                .append(StrUtil.SPACE)
                .append(StrUtil.EQ)
                .append(StrUtil.SPACE)
                .append(ciRight.cndColumnStr());

        return queryJoin;
    }
}
