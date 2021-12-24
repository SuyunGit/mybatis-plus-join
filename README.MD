## 基于Mybatis-Plus封装的多表关联查询器，优雅且美好

`为了解决简单的多表查询而生，避免编辑mapper.xml文件的尴尬，无需过多的配置，多行链式调用一撸到底`

> 项目地址：
> - github：`https://github.com/SuyunGit/mybatis-plus-join`
> - gitee：`https://gitee.com/suyungit/mybatis-plus-join`

> #### 它具备哪些特点呢？
> 1. 最重要的就是不需要再写xml了
> 2. 再就是全部可以使用链式调用，基于JDK8的Lambda表达式
> 3. 再次解放双手，字段的查询和字段条件的设置可以全部使用函数式
> 4. 本身查询返回的是Map对象，同时内置Map转实体对象
> 5. 可以进行分页查询
> 6. 只要你愿意，关联100个表都没问题
> 7. 查询的字段支持自定义别名
> 8. 支持JOIN，INNER JOIN，CROSS JOIN，LEFT JOIN，RIGHT JOIN；当然也支持WHERE JOIN
> 9. 提供返回完整SQL语句的函数
> 10. 内置条件值的自动判空函数
> 11. 支持多租户和逻辑删除配置
> 12. 支持开启DISTINCT关键字
> 13. IN SQL和NOT IN SQL优化，传入参数元素数量为1，自动转为等于或不等于
> 14. .....
> #### 它的缺点
> 1. 可能代码式的SQL不如xml式的SQL更直观
> 2. 刚开始使用可能会觉得麻烦
> 3. 不熟悉Lambda的人可能觉得使用起来会不爽
> 4. WHERE关联和JOIN关联可以混搭，但会有先后关连顺序的问题
> 5. 无法完美支持子查询，子查询代码片段需要手动apply
> 6. 无法完美支持SQL函数，需要调用SQL函数，只能手动了
> 6. 其它更多问题有待发现...
> 
> #### 预想实现
> 1. 首先是内置常用SQL函数
> 2. 想方设法的友好支持子查询
> 3. 支持关联到指定表
> 4. 支持SQL优化
> 5. 暂时就想到这么多

> #### 如何使用
> 1. 第一步，先下载代码，可以直接把代码集成到项目中去，也可以打包添加到项目依赖中（注意mybatis-plus版本需要在3.4.0以上，JDK版本需要在1.8以上）
> - 大家也可以用我发布的包，具体的版本请参考[MODIFY.MD](./MODIFY.MD)，有时间我会多测试修改并发布
> - 仓库地址：`http://nexus.sigment.cn/repository/maven-group-xgkeeper/`
> ```xml
>   <dependency>
>       <groupId>org.join.plus</groupId>
>       <artifactId>mybatis-plus-join</artifactId>
>       <version>${mpj.version}</version>
>   </dependency>
> ```
> 2. 打包：项目根目录中执行`mvn clean package`
> 3. 第二步，实现`JoinConfig`的接口，否则将使用默认配置
> 4. 第三步，Mapper接口需要先继承`JoinMapper`
> 5. 第四步开始构建`QueryJoin`
> 6. 第五步使用继承了`JoinMapper`的mapper调用构建好的`QueryJoin`
> 7. 也可以在构建`QueryJoin`的时候，把继承了`JoinMapper`的mapper的bean传递到`QueryJoin`中，然后使用`QueryJoin`内部的执行函数

> #### 根据SQL进行构建
> ##### 我们假设现在有一下几张表：
> - 一张用户表`tb_user(id:int, name:string, user_mobile:string, user_sex:boolean)`
> - 一张角色表`tb_role(id:int, role_name:string)`
> - 一张部门表`tb_dept(id:int, dept_name)`
> - 一张用户角色中间表`rel_user_role(id:int, user_id:int, role_id:int)`
> - 一张用户部门中间表`rel_user_dept(id:int, user_id:int, dept_id:int)`
> ##### 用户表对应的实体类示例
> ```java
> import com.baomidou.mybatisplus.annotation.TableId;
> import com.baomidou.mybatisplus.annotation.TableName; 
> import lombok.Builder; 
> import lombok.Data;import lombok.EqualsAndHashCode;
> import lombok.experimental.Accessors;
> import lombok.experimental.Tolerate;
> 
> @Data
> @Builder
> @Accessors(chain = true)
> @TableName("tb_user")
> @EqualsAndHashCode(callSuper = true)
> public class TbUser extends Model<TbUser> {
>     @Tolerate
>     public TbUser() {
>     }
> 
>     @TableId
>     private Integer id;
>     private String name;
>     private String userMobile;
>     private String userSex;
> }
> ```
> - 其它的就不写出来了

> #### SQL和代码实现
> - `SELECT tbUser.id AS id, tbUser.name AS name, tbUser.user_mobile AS userMobile, tbUser.user_sex AS user_sex FROM tb_user AS tbUser`
> ```java 
> QueryJoin.create(TbUser.class, joinConfig).listMap(); 
> ```
> - `SELECT tbUser.id AS id, tbUser.name AS name, tbUser.user_mobile AS userMobile, tbUser.user_sex AS user_sex FROM tb_user AS tbUser LIMIT 0, 10`
> ```java 
> QueryJoin.create(TbUser.class, joinConfig).pageMap(new Page<>(0, 10)); 
> ```
> - `SELECT tbUser.id AS userId, tbUser.name AS userName FROM tb_user AS tbUser WHERE (tbUser.id = 1) GROUP BY tbUser.id ORDER BY tbUser.id DESC`
> ```java
> QueryJoin.create(TbUser.class, joinConfig)
>     .select(TbUser::getId, "userId")
>     .select(TbUser::getName, "userName")
>     .eq(TbUser::getId, 1)
>     .orderByDesc(TbUser::getId)
>     .groupBy(TbUser::getId)
>     .listMap();
> ```
> - `SELECT tbUser.id AS id, tbUser.name AS name, tbRole.role_name AS roleName FROM tb_user AS tbUser LEFT JOIN rel_user_role AS relUserRole ON tbUser.id = relUserRole.user_id LEFT JOIN tb_role AS tbRole ON relUserRole.role_id = tbRole.role_id`
> ```java
> QueryJoin.create(TbUser.class, joinConfig)
>     .selects(TbUser::getId, TbUser::getName)
>     .select(TbRole::getRoleName)
>     .leftJoin(RelUserRole.class)
>     .on(TbUser::getId, RelUserRole::getUserId)
>     .leftJoin(TbRole.class)
>     .on(RelUserRole::getRoleId, TbRole::getId)
>     .listMap();
> ```
> 
> - `SELECT tbUser.id AS id, (SELECT COUNT(1) FROM tb_role) AS roles FROM tb_user AS tbUser`
> ```java
> QueryJoin.create(TbUser.class, joinConfig)
>     .select(TbUser::getId)
>     .select("(SELECT COUNT(1) FROM tb_role) AS roles")
>     .listMap();
> ```
> `至于租户和逻辑删除，大家就自己摸索一下吧，有问题需要探讨的，大家可以私我邮箱：1501722899@qq.com`

### `最后，希望大家可以帮忙多提issue和建设性建议，也很欢迎大家一起升级，进行壮大`
