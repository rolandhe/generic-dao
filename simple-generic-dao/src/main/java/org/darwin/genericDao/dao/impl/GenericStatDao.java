/**
 * org.darwin.genericDao.dao.impl.GenericStatDao.java created by Tianxin(tianjige@163.com) on
 * 2015年6月3日 下午2:05:00
 */
package org.darwin.genericDao.dao.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.darwin.common.utils.Utils;
import org.darwin.genericDao.annotations.StatType;
import org.darwin.genericDao.annotations.enums.QueryColumnFormat;
import org.darwin.genericDao.annotations.enums.Type;
import org.darwin.genericDao.dao.BaseStatDao;
import org.darwin.genericDao.mapper.ColumnMapper;
import org.darwin.genericDao.operate.Groups;
import org.darwin.genericDao.operate.Matches;
import org.darwin.genericDao.operate.Orders;
import org.darwin.genericDao.param.SQLParams;
import org.darwin.genericDao.query.QueryStat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统计表的默认DAO实现
 * created by Tianxin on 2015年6月3日 下午2:05:00
 */
public class GenericStatDao<ENTITY> extends AbstractGenericDao<ENTITY> implements BaseStatDao<ENTITY> {

  /**
   * static slf4j logger instance
   */
  protected final static Logger LOG = LoggerFactory.getLogger(GenericStatDao.class);

  /**
   * 无参构造函数
   */
  public GenericStatDao() {
    super();
    this.analysisColumns(columnMappers);
  }

  /**
   * 分析statObject中的所有列类型
   * 
   * @param columnMappers created by Tianxin on 2015年6月3日 下午3:56:37
   */
  private void analysisColumns(Map<String, ColumnMapper> columnMappers) {
    Collection<ColumnMapper> mappers = columnMappers.values();
    for (ColumnMapper mapper : mappers) {

      //获取statType与操作符
      StatType statType = mapper.getType();
      Type type = statType == null ? Type.KEY : statType.value();

      // 将字段处理好之后放到columns中
      String sqlColumn = mapper.getSQLColumn();
      if (type.value() != null) {
        sqlColumn = Utils.concat(type.value(), "(", sqlColumn, ") as ", sqlColumn);
      }
      allColumns.add(sqlColumn);
      dbColumnMapping.put(mapper.getSQLColumn(),sqlColumn);

      //时间字段
      if (type == Type.DATE) {
        keyColumns.add(mapper);
        dateColumn = mapper;
      }

      //key字段
      if (type == Type.KEY) {
        keyColumns.add(mapper);
      }
    }
  }

  protected ColumnMapper dateColumn = null;
  protected List<ColumnMapper> keyColumns = new ArrayList<ColumnMapper>();
  protected List<String> allColumns = new ArrayList<String>();
  protected Map<String,String> dbColumnMapping = new HashMap<>();

  /**
   * 获取数据，如果需要按时间聚合，则按其他字段groupBy，把不同时间的汇总到一起
   * 
   * @param aggregationByDate
   * @return created by Tianxin on 2015年6月3日 下午8:15:49
   */
  public List<ENTITY> statAll(boolean aggregationByDate) {
    Groups groups = aggregationByDate ? generateAggergationByDateGroups() : Groups.init();
    return statByMgo(null, groups, null);
  }

  /**
   * 根据匹配条件，分组规则，排序规则进行统计
   * 
   * @param matches
   * @return created by Tianxin on 2015年6月4日 下午8:23:27
   */
  public ENTITY statByMatches(Matches matches) {
    List<ENTITY> entities = statByMgo(matches, null, null);

    if (Utils.isEmpty(entities)) {
      return null;
    }

    return entities.get(0);
  }

  /**
   * 根据匹配条件，分组规则，排序规则进行统计
   *
   * @param matches
   * @param groups
   * @param orders
   * @param column 列名称，对应ENTITY的的属性，可以是属性名也可以是数据库字段名称
   * @param eClass
   * @param <E>
   * @return
   *
   * created by Tianxin on 2015年6月4日 下午8:23:27
   */
  public <E> List<E> statOneColumnByMgo(Matches matches, Groups groups, Orders orders, String column, Class<E> eClass) {
    return statPageOneColumnByMgo(matches, groups, orders, column, eClass, 0, 0);
  }

  /**
   * 根据匹配条件，分组规则，排序规则进行统计
   *
   * @param matches
   * @param groups
   * @param orders
   * @param column 列名称，对应ENTITY的的属性，可以是属性名也可以是数据库字段名称
   * @param eClass
   * @param offset
   * @param rows
   * @param <E>
   * @return
   *
   * @return created by Tianxin on 2015年6月4日 下午8:23:27
   */
  public <E> List<E> statPageOneColumnByMgo(Matches matches, Groups groups, Orders orders, String column, Class<E> eClass, int offset, int rows) {
    QueryStat query = new QueryStat(Arrays.asList(dbColumnMapping.get(columnNameConverter.convert(column))), matches, groups, orders, table(), offset, rows);
    String sql = query.getSQL(columnNameConverter);
    Object[] params = query.getParams();
    LOG.info(Utils.toLogSQL(sql, params));
    return findBySQL(eClass, sql, params);
  }

  /**
   * 根据匹配条件，分组规则，排序规则进行统计
   * 
   * @param matches
   * @param groups
   * @param orders
   * @return created by Tianxin on 2015年6月4日 下午8:23:27
   */
  public List<ENTITY> statByMgo(Matches matches, Groups groups, Orders orders) {
    QueryStat query = new QueryStat(allColumns, matches, groups, orders, table());
    return statByQuery(query);
  }

  /**
   * 根据匹配条件，分组规则，排序规则进行统计
   * 
   * @param matches
   * @param groups
   * @param orders
   * @return created by Tianxin on 2015年6月4日 下午8:23:27
   */
  public List<ENTITY> statPageByMgo(Matches matches, Groups groups, Orders orders, int offset, int rows) {
    QueryStat query = new QueryStat(allColumns, matches, groups, orders, table(), offset, rows);
    return statByQuery(query);
  }

  /**
   * 根据匹配条件，分组规则，计算数据总数
   * 
   * @param matches
   * @param groups
   * @return created by Tianxin on 2015年6月4日 下午8:23:27
   */
  public int statCountByMg(Matches matches, Groups groups) {
    List<String> columns = groups.getGroupByColumns();
    return countDistinct(matches, columns.toArray(new String[columns.size()]));
  }

  /**
   * 按Query进行查询
   * 
   * @param query
   * @return created by Tianxin on 2015年6月3日 下午4:06:19
   */
  protected List<ENTITY> statByQuery(QueryStat query) {
    String sql = query.getSQL(columnNameConverter);
    Object[] params = query.getParams();
    return findBySQL(entityClass, sql, params);
  }

  /**
   * 查询某个时间范围的数据
   * 
   * @param startDate
   * @param endDate
   * @param aggregationByDate
   * @return created by Tianxin on 2015年6月3日 下午8:16:37
   */
  public List<ENTITY> statByRange(int startDate, int endDate, boolean aggregationByDate) {
    Matches matches = Matches.one(getUseColumnName(dateColumn), SQLParams.between(startDate, endDate));
    Groups groups = aggregationByDate ? generateAggergationByDateGroups() : Groups.init();
    return statByMgo(matches, groups, null);
  }

  /**
   * 当按时间聚合时，groupBy的字段为除去date字段的其他key字段
   * 
   * @return created by Tianxin on 2015年6月3日 下午8:16:55
   */
  private Groups generateAggergationByDateGroups() {
    Groups groups = Groups.init();
    for (ColumnMapper column : keyColumns) {
      if (!column.equals(dateColumn)) {
        groups.groupBy(getUseColumnName(column));
      }
    }
    return groups;
  }

  private String getUseColumnName(ColumnMapper mapper) {
    if(columnNameConverter.getColumnFormat() == QueryColumnFormat.POJO_FIELD_NAME) {
      return mapper.getFieldName();
    }
    return mapper.getSQLColumn();
  }
}
