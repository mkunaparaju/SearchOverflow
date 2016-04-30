package indexer;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.json.JSONException;
import org.json.JSONObject;

import parser.JsonParser;
import db.Database;

/**
 * Retriver that takes the index Path and the query and searches the index for matches.
 * @author Narasimman
 *
 */
public class Retriever {
  public static final int MAX_LIMIT = 100;
  private Map<Integer, Post> postsMap;
  private PriorityQueue<Post> orderedPosts;
  private final Database connection;

  public Retriever(String dbPath) throws ClassNotFoundException, SQLException {
    postsMap = new HashMap<Integer, Post>();
    connection = new Database(dbPath);
  }

  private Post search(String indexPath, String[] q)
      throws IOException, org.apache.lucene.queryparser.classic.ParseException, SQLException {
    Path path = FileSystems.getDefault().getPath(indexPath);
    Directory dir = FSDirectory.open(path);

    IndexReader reader = DirectoryReader.open(dir);
    IndexSearcher is = new IndexSearcher(reader);
    //QueryParser parser = new QueryParser(PostField.TITLE.toString(),
    //  new StandardAnalyzer());

    Analyzer analyzer = new StandardAnalyzer();
    MultiFieldQueryParser parser = new MultiFieldQueryParser(
        new String[] {PostField.TITLE.toString(), 
            PostField.BODY.toString()},
            analyzer);

    String queryStr = "";

    for (String s : q) {
      queryStr += s + " ";
    }

    Query query = parser.parse(queryStr);

    long start = System.currentTimeMillis();

    TopDocs hits = is.search(query, MAX_LIMIT);

    long end = System.currentTimeMillis();

    System.out.println("Found " + hits.totalHits + " document(s) (in "
        + (end - start) + " milliseconds) that matched query '" + queryStr);

    List<String> ansList = new ArrayList<String>();

    for (int i = 0; i < hits.scoreDocs.length; i++) {
      ScoreDoc scoreDoc = hits.scoreDocs[i];
      Document doc = is.doc(scoreDoc.doc);

      String answerId = doc.get(PostField.ACCEPTEDANSWERID.toString());
      if(answerId != null) {
        ansList.add(answerId);
      }

      postsMap.put(Integer.parseInt((doc.get(PostField.ID.toString()))),
          buildPost(doc));      
    }

    populateAnswers(ansList, true); 

    System.out.println(postsMap);
    computePostRanks();
    Post result = getTopPost();

    return result;
  }

  private Post getTopPost() {
    // TODO Auto-generated method stub
	  
    return orderedPosts.poll();
  }

  private void computePostRanks() {
	  // I have my post map
	  //I have the final Score field.
	  //Need user score
	  Comparator<Post> comparator = new PostComparator();
	  orderedPosts = new PriorityQueue<Post>(comparator);
	  
	  Iterator it = postsMap.entrySet().iterator();
	  while(it.hasNext())
	  {
		  Map.Entry postIdPair = (Map.Entry)it.next();
		  Post post = (Post)postIdPair.getValue();
		  double postScore = post.getWeightScore();
		  double ansScore = post.getAnsObj().getWeightedScore();
		  double userScore = post.getAnsObj().getWeightedUserScore();
		  double luceneScore = 0;
		  
		  double finalScore = postScore+ansScore+userScore+luceneScore;
		  post.setFinalScore(finalScore);
		  orderedPosts.add(post);		  
	  }  

  }

  public String retrieve(String indexPath, String query) 
      throws IOException, org.apache.lucene.queryparser.classic.ParseException, SQLException {
    Post bestPost = search(indexPath, query.split(" "));
    return bestPost.getBody();
  }

  private void populateAnswers(List<String> ansList, boolean isLocal) throws IOException, SQLException {
    if(isLocal) {
      String q = "Select Id, body, score, ParentId from Posts where PostTypeId='2' and Id in (";
      for(String id : ansList) {
        q += id + ","; 
      }

      //#TODO
      q = q.substring(0, q.length() - 1) + ")";
      ResultSet rs = connection.executeQuery(q);

      while(rs.next()) {        
        int parentId = rs.getInt("ParentId");
        int answerId = rs.getInt("Id");
        int score = rs.getInt("score");
        String body = rs.getString("body");
        Answer answer = new Answer(answerId, score, body);
        addToPost(parentId, answer);
      }

    } else {
      List<JSONObject> ansListJSON = JsonParser.getAnswers(ansList);
      addAnswer(ansListJSON);
    }
  }

  private void addToPost(int postId, Answer answer) {
    Post parentPost = postsMap.get(postId);
    parentPost.setAnswer(answer);
  }

  private void addAnswer(List<JSONObject> ansList) {
    for (JSONObject answer : ansList) {
      try {
        int parentId = answer.getInt("question_id");

        Post parentPost = postsMap.get(parentId);
        	
        int answerId = answer.getInt("answer_id");
        int score = answer.getInt("score");
        String body = answer.getString("body");
        JSONObject userObj = answer.getJSONObject("owner");
        
        
        Answer ans = new Answer(answerId, score, body);
        ans.setUserScore(userObj.getInt("reputation"));

        addToPost(parentId, ans);
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
  }

  public Post buildPost(Document doc) {
    int id = 0;
    int acceptedAnsId = 0;
    int score = 0;
    int viewCount = 0;
    int favCount = 0;

    if (doc.get(PostField.ID.toString()) != null) {
      id = Integer.parseInt(doc.get(PostField.ID.toString()));
    }

    if (doc.get(PostField.ACCEPTEDANSWERID.toString()) != null) {
      acceptedAnsId = Integer.parseInt(doc.get(PostField.ACCEPTEDANSWERID.toString()));
    }

    if (doc.get(PostField.SCORE.toString()) != null) {
      score = Integer.parseInt(doc.get(PostField.SCORE.toString()));
    }

    if (doc.get(PostField.VIEWCOUNT.toString()) != null) {
      viewCount = Integer.parseInt(doc.get(PostField.VIEWCOUNT.toString()));
    }

    if (doc.get(PostField.FAVORITECOUNT.toString()) != null) {
      favCount = Integer.parseInt(doc.get(PostField.FAVORITECOUNT.toString()));
    }

    Post post = new Post.PostBuilder(id)
    .acceptedAnswerId(acceptedAnsId)
    .score(score)
    .viewCount(viewCount)
    .favoriteCount(favCount).build();

    return post;
  }

  public static void main(String[] args) throws Exception {
    String usage = "Usage: " + Retriever.class.getName()
        + " [-index INDEX_PATH] [-q query terms]\n\n"
        + "This requires a path to the index file created by lucene"
        + " and query terms to search for";

    if (args.length < 2) {
      throw new Exception(usage);
    }
    CommandLine cmd = null;

    // set options
    Options options = new Options();
    options.addOption("index", "index", true, "Index Path");
    options.addOption("db", "db", true, "DB Path");
    Option qOption = new Option("q", "q", true, "Query");
    qOption.setArgs(Option.UNLIMITED_VALUES);

    options.addOption(qOption);

    CommandLineParser parser = new DefaultParser();

    try {
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      e.printStackTrace();
    }
    String indexPath = cmd.getOptionValue("index");
    String dbPath = cmd.getOptionValue("db");
    String[] query = cmd.getOptionValues("q");


    Retriever ret = new Retriever(dbPath);
    Post result = ret.search(indexPath, query);

    System.out.println(result);
  }
}
