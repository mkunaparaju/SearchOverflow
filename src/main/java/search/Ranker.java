package search;

import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;

import stackoverflow.Answer;
import stackoverflow.Post;
import stackoverflow.PostComparator;

/**
 * A Ranker class the ranks the list of post objects based on our ranking
 * algorithm
 * 
 * @author Narasimman
 * 
 */
public class Ranker {
  private PriorityQueue<Post> orderedPosts;
  private final Map<Integer, Post> postsMap;

  /**
   * 
   * @param postsMap
   */
  public Ranker(Map<Integer, Post> postsMap) {
    this.postsMap = postsMap;
  }

  /**
   * Compute the page rank designed by Manasa
   */
  public void computePostRanks() {
    Comparator<Post> comparator = new PostComparator();
    orderedPosts = new PriorityQueue<Post>(Retriever.MAX_LIMIT, comparator);

    for (Post post : postsMap.values()) {
      // double postScore = post.getWeightScore();

      Answer ans = post.getAnswer();

      double ansScore = 0.0;
      double userScore = 0.0;
      double normAnsScore = 0.0;
      double normUserScore = 0.0;
      if (ans != null) {
        ansScore = post.getAnswer().getScore();
        normAnsScore = 1 / (1 + (Math.exp(-ansScore)));
        userScore = post.getAnswer().getUserScore();
        normUserScore = 1 / (1 + (Math.exp(-userScore)));
      }
      double luceneScore = post.getLuceneScore();
      double finalScore = 0.85 * (luceneScore) + 0.15 * normAnsScore;
      post.setFinalScore(finalScore);
      orderedPosts.add(post);
    }
  }

  public Post getTopPost() {
    return orderedPosts.peek();
  }
}