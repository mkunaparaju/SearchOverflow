package search;import java.io.IOException;import org.apache.lucene.document.Document;import org.apache.lucene.index.LeafReaderContext;import org.apache.lucene.queries.CustomScoreProvider;import org.apache.lucene.queries.CustomScoreQuery;import org.apache.lucene.search.Query;import stackoverflow.PostField;public class MyOwnScoreQuery extends CustomScoreQuery {  private Query query;  private float maxScore = 0f;  public MyOwnScoreQuery(Query query) {    super(query);    this.query = query;  }  @Override  public CustomScoreProvider getCustomScoreProvider(final LeafReaderContext context) {    return new CustomScoreProvider(context) {      @Override      public float customScore(int doc,          float subQueryScore,          float valSrcScore) throws IOException {        Document document = context.reader().document(doc);        float score = 1f;        if(document.getField(PostField.SCORE.toString()) != null &&             !document.getField(PostField.SCORE.toString()).stringValue().isEmpty()) {          score = Integer.parseInt(document.getField(PostField.SCORE.toString()).stringValue());                  }        float lucene = super.customScore(doc, subQueryScore, valSrcScore);        return (float)(1/(1+Math.exp(-score))) * 0.25f + (float)(1/(1+Math.exp(-lucene))) * 0.75f;      }    };  }}