### 版本更新内容
>2021年12月24日
- 1.`QueryJoin`新增内置判断值是否为空函数
- 2.`QueryJoin`新增可以指定表别名的关联函数
- 3.`QueryJoin`新增UNION和UNION ALL
- 4.可以通过定义页码和页容量设置分页对象
- 5.当多次关联同一个实体时，表别名自动添加自增的数字后缀
~~~
Ex:
    queryJoin.innerJoin(A.class)
    ...
    .leftJoin(A.class)
    ...
则最终的SQL语句是：
    SELECT ... FROM ... INNER JOIN a AS a ON ... LEFT JOIN a AS a1 ON ...
~~~
- 6.使用UNION相关语句时，不建议超过5个，这会使得代码的可读性急剧下降，造成维护困难
- 7.增加查询单个值的方法：
  - 查询结果不超过1且指定转换类型`QueryJoin#oneValue(Class)`
  - 此方法可以避免多个结果造成的异常，直接取第一个`QueryJoin#oneValue(boolean, Class<?>)`
- 8.增加查询单个值集合的方法：
  - 对返回的结果集指定字段属性返回`QueryJoin#entityValueList(SFunction)`
  - 对返回的结果集取每个Map中第一个值并指定类型返回`QueryJoin#toValueList(Class)`
  - 对返回的结果集取每个Map中某个属性并指定类型返回`QueryJoin#toValueList(Class, String)`
- 9.修复之前使用过程中出现的问题
- 10.更新版本至`1.1.0-RELEASE`
- 11.感谢使用者在使用过程中验证的问题以及提出的宝贵意见👍
---
>2021年8月27日
- 1.修复使用like函数时，内部调用的是le函数
---
> 2021年8月26日
- 1.开放page的设置，为page属性增加setter和getter
- 2.版本号升级为`1.0.5-RELEASE`
- 3.修改page的setter函数返回QueryJoin对象，版本号更新为`1.0.6-RELEASE`
---
> 2021年8月24日
- 1.增加一个count函数
---
> 2021年8月23日
- 1.新增获取单一结果的方法
- 2.单一结果返回可自定义实体类型，也可以直接获取Map结果
- 3.可定义是否抛出多条异常
- 4.版本号更改为 `1.0.2-RELEASE`

