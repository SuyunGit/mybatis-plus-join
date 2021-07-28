package org.join.plus.common;

/**
 * @author suyun
 * @date 2021-07-19 16:49
 */
public enum JoinType {
    /**
     * 只是 JOIN
     */
    JOIN,
    /**
     * INNER JOIN
     */
    INNER,
    /**
     * CROSS JOIN
     */
    CROSS,
    /**
     * LEFT JOIN
     */
    LEFT,
    /**
     * RIGHT JOIN
     */
    RIGHT,
    /**
     * 使用WHERE条件进行关联
     */
    WHERE;
}
