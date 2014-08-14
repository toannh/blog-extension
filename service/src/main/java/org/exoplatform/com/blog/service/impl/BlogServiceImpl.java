/*
 * Copyright (C) 2003-2014 eXo Platform SEA.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */

package org.exoplatform.com.blog.service.impl;

import org.exoplatform.com.blog.service.BlogService;
import org.exoplatform.com.blog.service.entity.BlogArchive;
import org.exoplatform.services.cms.comments.CommentsService;
import org.exoplatform.services.cms.drives.DriveData;
import org.exoplatform.services.cms.drives.ManageDriveService;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by The eXo Platform SAS
 * Author : eXoPlatform
 * exo@exoplatform.com
 * Aug 4, 2014
 */
public class BlogServiceImpl implements BlogService {
  private Log log = ExoLogger.getExoLogger(BlogService.class);

  private static final String BLOG_NODE = "exo:blog";
  private static final String DRIVER_PATH = "Blog";
  private static final String EXO_DATE_CREATED = "exo:dateCreated";
  private static final String BLOG_APPROVE_NODE = "exo:blogApprove";
  private static final String BLOG_VOTE_NODE = "exo:blogVote";
  private static final String BLOG_VOTE_POINT_PROPERTY = "exo:blogVotePoint";
  private static final String BLOG_VOTE_TOTAL_PROPERTY = "exo:blogVoteTotal";
  private static final String BLOG_STATUS_PROPERTY = "exo:blogStatus";

  //comment
  private final static String COMMENT_CREATED_DATE = "exo:commentDate";
  private final static String COMMENT_MESSAGE = "exo:commentContent";

  private static final String TIME_FORMAT_TAIL = "T00:00:00.000";
  private static final SimpleDateFormat formatDateTime = new SimpleDateFormat();

  private boolean initData = true;
  private String repo = "repository";
  private String ws = "collaboration";

  private RepositoryService repoService;
  private SessionProviderService sessionProviderService;
  private ManageDriveService manageDriveService;
  private CommentsService commentsService;

  private Map<Integer, BlogArchive> blogArchives = new HashMap<Integer, BlogArchive>();

  static {
    formatDateTime.applyPattern("yyyy-MM-dd");
  }

  private void add(Integer year, Integer month) {
    BlogArchive blogArchive;
    Map<Integer, Integer> _month;
    if (!this.blogArchives.containsKey(year)) { // year doesnt exits
      _month = new HashMap<Integer, Integer>();
      blogArchive = new BlogArchive();
      blogArchive.setYear_post(1);
      _month.put((Integer) month, 1);
      blogArchive.setMonth(_month);
      this.blogArchives.put(year, blogArchive);
    } else { // year exits
      blogArchive = this.blogArchives.get(year);
      blogArchive.setYear_post(blogArchive.getYear_post() + 1);
      _month = blogArchive.getMonth();
      if (!_month.containsKey(month)) { //month of year doesnt exits
        _month.put((Integer) month, 1);
      } else {
        _month.put((Integer) month, _month.get(month) + 1);
      }
      blogArchive.setMonth(_month);
      this.blogArchives.put(year, blogArchive);
    }
  }

  public BlogServiceImpl(RepositoryService repoService, SessionProviderService sessionProviderService,
                         ManageDriveService managerDriverService) {
    this.manageDriveService = managerDriverService;
    this.repoService = repoService;
    this.sessionProviderService = sessionProviderService;
    this.commentsService = WCMCoreUtils.getService(CommentsService.class);

    try {
      this.repo = repoService.getCurrentRepository().getConfiguration().getName();
      this.ws = repoService.getCurrentRepository().getConfiguration().getDefaultWorkspaceName();
    } catch (Exception ex) {
      if (log.isErrorEnabled()) {
        log.error("Using default repository & workspace", ex.getMessage());
      }
    }
    initBlogArchive();
    setInitData(false);
  }

  void initBlogArchive() {
    if (isInitData()) {
      try {
        Session session = getSystemSession();
        List<Node> allNode = getAllNode(BLOG_NODE, session);
        Calendar cal = null;
        for (Node node : allNode) {
          cal = node.getProperty(EXO_DATE_CREATED).getValue().getDate();
          int _year = cal.get(Calendar.YEAR);
          int _month = cal.get(Calendar.MONTH);
          add(_year, _month);
        }
      } catch (Exception ex) {
        if (log.isErrorEnabled()) {
          log.error(ex.getMessage());
        }
      }
    }
  }

  public boolean isInitData() {
    return initData;
  }

  public void setInitData(boolean initData) {
    this.initData = initData;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Integer> getArchiveYears() {
    return new ArrayList<Integer>(blogArchives.keySet());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Integer> getArchiveMonths(int year) {
    BlogArchive monthByYear = this.blogArchives.get(year);
    if (monthByYear != null && monthByYear.getMonth() != null)
      return new ArrayList<Integer>(monthByYear.getMonth().keySet());
    return new ArrayList<Integer>();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getArchivesCountInYear(int year) {
    BlogArchive blogArchive = this.blogArchives.get(year);
    if (blogArchive != null)
      return blogArchive.getYear_post();
    return 0;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getArchivesCountInMonth(int year, int month) {
    BlogArchive monthByYear = this.blogArchives.get(year);
    if (monthByYear != null && monthByYear.getMonth() != null && monthByYear.getMonth().get(month) != null)
      return monthByYear.getMonth().get(month);
    return 0;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Node> getBlogs(int year, int month) {
    try {
      return getAllNode(BLOG_NODE, getStrFirstDayOfMonth(year, month), getStrLastDayOfMonth(year, month));
    } catch (Exception ex) {
      if (log.isErrorEnabled()) {
        log.error(ex.getMessage());
      }
    }
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void addBlog(Node blogNode) {
    try {
      if (blogNode.isNodeType(BlogServiceImpl.BLOG_NODE)) {
        Calendar cal = new GregorianCalendar();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        add(year, month);
      }
    } catch (RepositoryException ex) {
      if (log.isErrorEnabled()) {
        log.error(ex.getMessage());
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void removeBlog(Node blogNode) {
    try {
      if (blogNode.isNodeType(BlogServiceImpl.BLOG_NODE)) {
        Calendar cal = blogNode.getProperty("exo:dateCreated").getDate();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        BlogArchive _blogYearArchive = blogArchives.get(year);

        if (_blogYearArchive == null) return;

        //decrease year
        blogArchives.get(year).setYear_post(_blogYearArchive.getYear_post() - 1);
        Map<Integer, Integer> monthByYear = _blogYearArchive.getMonth();

        //decrease month
        if (monthByYear.containsKey(month)) {
          blogArchives.get(year).getMonth().put(month, monthByYear.get(month) - 1);
        }
      }
    } catch (PathNotFoundException ex) {
      if (log.isErrorEnabled()) {
        log.error(ex.getMessage());
      }
    } catch (RepositoryException ex) {
      if (log.isErrorEnabled()) {
        log.error(ex.getMessage());
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Node changeStatus(String nodePath) {
    try {
      Session session = getSession();
      if (nodePath.startsWith("/")) nodePath = nodePath.substring(1);

      Node nodeUpdate = session.getRootNode().getNode(nodePath);
      if (nodeUpdate.canAddMixin(BLOG_APPROVE_NODE)) {
        nodeUpdate.addMixin(BLOG_APPROVE_NODE);
        nodeUpdate.setProperty(BLOG_STATUS_PROPERTY, true);
      } else {
        boolean status = nodeUpdate.getProperty(BLOG_STATUS_PROPERTY).getBoolean();
        nodeUpdate.setProperty(BLOG_STATUS_PROPERTY, !status);
      }
      session.save();
      return nodeUpdate;
    } catch (Exception ex) {
      if (log.isErrorEnabled()) log.error(ex.getMessage());
    }
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Node getPost(String postPath) {
    try {
      Session session = getSession();
      return (Node) session.getItem(postPath);
    } catch (Exception ex) {
      if (log.isErrorEnabled()) {
        log.error(ex.getMessage());
      }
    }
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Node updateVote(String postNodePath, int score) {
    try {
      Session session = getSession();
      if (postNodePath.startsWith("/")) postNodePath = postNodePath.substring(1);
      Node nodeUpdate = session.getRootNode().getNode(postNodePath);

      if (nodeUpdate.canAddMixin(BLOG_VOTE_NODE)) {
        nodeUpdate.addMixin(BLOG_VOTE_NODE);
        nodeUpdate.setProperty(BLOG_VOTE_POINT_PROPERTY, 1);
        nodeUpdate.setProperty(BLOG_VOTE_TOTAL_PROPERTY, score);
      } else {
        int _currentVotePoint = Integer.parseInt(nodeUpdate.getProperty(BLOG_VOTE_POINT_PROPERTY).getString());
        int _currentVoteTotal = Integer.parseInt(nodeUpdate.getProperty(BLOG_VOTE_TOTAL_PROPERTY).getString());
        nodeUpdate.setProperty(BLOG_VOTE_POINT_PROPERTY, _currentVotePoint + 1);
        nodeUpdate.setProperty(BLOG_VOTE_TOTAL_PROPERTY, _currentVoteTotal + score);
      }
      session.save();
      return nodeUpdate;
    } catch (Exception ex) {
      if (log.isErrorEnabled()) log.error(ex.getMessage());
    }
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean editComment(String nodePath, String newComment) {
    Node nodeEdit = getPost(nodePath);
    try {
      commentsService.updateComment(nodeEdit, newComment);
      return true;
    }catch(Exception ex){if(log.isErrorEnabled())log.error(ex.getMessage());}
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean delComment(String nodePath) {
    Node nodeDelete = getPost(nodePath);
    try {
      commentsService.deleteComment(nodeDelete);
      return true;
    }catch(Exception ex){if(log.isErrorEnabled())log.error(ex.getMessage());}
    return false;
  }

  @Override
  public boolean vote(Node document, double rate, String userName) {
    return false;
  }

  @Override
  public long getVoteTotal(Node node) {
    return 0;
  }

  @Override
  public boolean isVoted(Node node, String userName) {
    return false;
  }

  @Override
  public double getVoteValueOfUser(Node node, String userName) {
    return 0;
  }

  /**
   * Get All node of element
   *
   * @param nodeElement
   * @return
   * @throws Exception
   */
  private List<Node> getAllNode(String nodeElement, Session session) throws Exception {
//    Session session = getSession();
    String searchPath = getDriverPath();
    List<Node> rs = new ArrayList<Node>();
    StringBuilder queryBuilder = new StringBuilder("SELECT * FROM ").append(nodeElement);
    queryBuilder.append(" WHERE jcr:path LIKE '" + searchPath + "' ");
    queryBuilder.append(" ORDER BY exo:dateCreated DESC ");
    QueryManager queryManager = session.getWorkspace().getQueryManager();
    Query query = queryManager.createQuery(queryBuilder.toString(), Query.SQL);

    NodeIterator nodes = query.execute().getNodes();
    while (nodes.hasNext()) {
      rs.add(nodes.nextNode());
    }
    return rs;
  }

  /**
   * Get All node by node, month
   *
   * @param nodeElement
   * @param firstDayOfMonth
   * @param lastDayOfMonth
   * @return
   * @throws Exception
   */
  private List<Node> getAllNode(String nodeElement, String firstDayOfMonth, String lastDayOfMonth) throws Exception {
    Session session = getSession();
    List<Node> rs = new ArrayList<Node>();
    String searchPath = getDriverPath();
    StringBuilder queryBuilder = new StringBuilder("SELECT * FROM ").append(nodeElement);
    queryBuilder.append(" WHERE jcr:path LIKE '" + searchPath + "' ");
    queryBuilder.append(" AND   exo:dateCreated >= TIMESTAMP '" + firstDayOfMonth + "' ");
    queryBuilder.append(" AND   exo:dateCreated <= TIMESTAMP '" + lastDayOfMonth + "' ");
    queryBuilder.append(" ORDER BY exo:dateCreated DESC ");
    QueryManager queryManager = session.getWorkspace().getQueryManager();
    Query query = queryManager.createQuery(queryBuilder.toString(), Query.SQL);

    NodeIterator nodes = query.execute().getNodes();
    while (nodes.hasNext()) {
      rs.add(nodes.nextNode());
    }
    return rs;
  }

  /**
   * Get session
   *
   * @return
   * @throws Exception
   */
  private Session getSession() throws Exception {
    ManageableRepository repository = repoService.getRepository(this.repo);
    SessionProvider sessionProvider = sessionProviderService.getSessionProvider(null);
    Session session = sessionProvider.getSession(this.ws, repository);
    return session;
  }

  private Session getSystemSession() throws Exception {
    ManageableRepository repository = repoService.getRepository(this.repo);
    SessionProvider sessionProvider = sessionProviderService.getSystemSessionProvider(null);
    Session session = sessionProvider.getSession(this.ws, repository);
    return session;
  }

  private String getStrFirstDayOfMonth(int year, int month) {
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.YEAR, year);
    cal.set(Calendar.MONTH, month);
    cal.set(Calendar.DAY_OF_MONTH, 1);
    return formatDateTime.format(cal.getTime()) + TIME_FORMAT_TAIL;
  }

  private String getStrLastDayOfMonth(int year, int month) {
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.YEAR, year);
    cal.set(Calendar.MONTH, month + 1);
    cal.set(Calendar.DAY_OF_MONTH, 1);
    return formatDateTime.format(cal.getTime()) + TIME_FORMAT_TAIL;
  }

  private String getDriverPath() throws Exception {
    DriveData driveData = manageDriveService.getDriveByName(DRIVER_PATH);
    String driverPath = driveData.getHomePath();
    if (driverPath != null) driverPath = driverPath.substring(0, driverPath.lastIndexOf("/") + 1);
    driverPath += "%";
    return driverPath;
  }
}
