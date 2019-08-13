package actions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import database.DBHelper;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.StringUtils;
import pos.POSTagger;
import util.Query;
import util.QueryResults;

public class NLPAction {
	public void genQuery(String query){
		String[] tokens = query.split("\\s+");
		String[] parsed = new String[tokens.length];
		String[] notparsed = new String[tokens.length];
		boolean broken = false;
		
		 String grammar = "models/englishPCFG.ser.gz";
		    String[] options = { "-maxLength", "80", "-retainTmpSubcategories" };
		    LexicalizedParser lp = LexicalizedParser.loadModel(grammar, options);
		    TreebankLanguagePack tlp = lp.getOp().langpack();
		    GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();

		    Iterable<List<? extends HasWord>> sentences;
		      // Showing tokenization and parsing in code a couple of different ways.
		    	String in=query;
		    	
		    	String[] sent = in.split("\\s+");
		    	List<HasWord> sentence = new ArrayList<>();
		      for (String word : sent) {
		        sentence.add(new Word(word));
		      }
		      List<List<? extends HasWord>> tmp =
		              new ArrayList<>();
		      tmp.add(sentence);
		      sentences = tmp;

		    for (List<? extends HasWord> sentence1 : sentences) {
		      Tree parse = lp.parse(sentence1);
		      parse.pennPrint();
		     // System.out.println();
		      GrammaticalStructure gs = gsf.newGrammaticalStructure(parse);
		      List<TypedDependency> tdl = gs.typedDependenciesCCprocessed();
		      String sqlQuery = "";
		      String select = "";
		      String where = "";
		      ArrayList<String> dependency = new ArrayList<String>();
		      for(TypedDependency td: tdl){
		    	  String gov = (td.gov()).toString();
			      String dep = (td.dep()).toString();
			      String reln = (td.reln()).toString();
			      System.out.println("gov:"+gov+"dep:"+dep);
			      if(reln.contains("subj")){			    	  
			    	  if(gov.contains("/NN") || gov.contains("/NNS") || gov.contains("/JJ")){
			    		 select = select + gov+",";
			    	  }
			    	  if(dep.contains("/NN") || dep.contains("/NNS") || dep.contains("/JJ")){
			    		  select = select + dep+",";
			    	  }
			      }
			    	if(reln.contains("mod")){
			    		if(gov.contains("/NN") && dep.contains("/NN")){
			    			dependency.add(dep+","+gov);
			    		}
			    		if(gov.contains("/NN") || gov.contains("/NNS") || gov.contains("/JJ")){
				    		 where = where + gov+",";
				    	  }
				    	  if(dep.contains("/NN") || dep.contains("/NNS") || dep.contains("/JJ")){
				    		  where = where + dep+",";
				    	  }
			    	}  
		      }
		    	 System.out.println("Select: "+select);
		    	 System.out.println("where: "+where);
		      //System.out.println("List:"+tdl);
		    	 String arrSelect[] = select.split(",");
		    	 Set<String> selectList = new HashSet<String>();
		    	 for(String s : arrSelect){
		    		 s = cleanString(s);
		    		 selectList.add(s);
		    	 }
		    	 String arrWhere[] = where.split(",");
		    	 Set<String> whereList = new HashSet<String>();
		    	 for(String s : arrWhere){
		    		 s = cleanString(s);
		    		 whereList.add(s);
		    	 }
		    	 System.out.println("Selectlist"+selectList);
		    	 System.out.println("wherelist"+whereList);
		    	 DBHelper db = new DBHelper();
		    	 HashMap<String, ArrayList> map = new HashMap<String,ArrayList>();
		    	 for(String s1 : selectList){		    		 
		    		map =  db.compareKeyword(s1);
		    	 }
		    	 HashMap<String, ArrayList> map1 = new HashMap<String,ArrayList>();
		    	 for(String s1 : whereList){		    		 
			    		map1 =  db.compareKeyword(s1);
			    	 }
		    	 
		    	 ArrayList pairList = null;
		    	 if(map1.get("col") != null){
		    		 Query queryObj;
		    		 ArrayList<String> col = map1.get("tab");
		    		 boolean pairFound = false;
		    		 if(col !=null){
		    			 for(String s:col){
		    				 for(String s1:dependency){
		    					 String arr[] = s1.split(",");
		    					 queryObj = new Query();
		    					 if(s1.contains(arr[0])){
		    						 pairFound = true;
		    						 queryObj.setColPair(arr[0]+"="+arr[1]);
		    					 }
		    					 else if(s1.contains(arr[1])){
		    						 pairFound = true;
		    						 queryObj.setColPair(arr[1]+"="+arr[0]);
		    					 }
		    					 if(pairFound){
		    						 pairList = new ArrayList();
		    						 pairList.add(queryObj);
		    					 }
		    				 }
		    			 }
		    		 }
		    	 }
		    	 System.out.println("map:"+map+"map2"+map1+"list"+pairList);
		    	 generateQuery(map, map1,pairList);
		    	 System.out.println("done");
		    }
	}
	public void generateQuery(HashMap<String,ArrayList> selectMap, HashMap<String,ArrayList> whereMap,ArrayList<Query> pairlist){
		ArrayList selectList = selectMap.get("col");
		ArrayList selectTab = selectMap.get("table");
		ArrayList whereList = whereMap.get("col");
		ArrayList whereTab = whereMap.get("table");
		String selectQuery = StringUtils.join(selectList,",");
		String whereQuery = StringUtils.join(whereList,",");
		String fromQuery = StringUtils.join(selectTab,",");
		String subQuery = "";
		String subsubQuery = "";
		String pair = (StringUtils.join(pairlist,",")!=null)?StringUtils.join(pairlist,","):"";
		if(selectQuery.contains("winner")){
			whereQuery = whereQuery + "winner='Y'";
			selectQuery = "*";
			fromQuery = " candidatemaster where npid=";
			subQuery = "(select npid from winnermaster where county_id=(select county_id from countymaster where "+pair+") and winner='Y')";
		}
		String selQuery = "select "+selectQuery+" from "+fromQuery+subQuery;
		System.out.println(selQuery);
		QueryResults results = new QueryResults();
		System.out.println(results.getResults(selQuery));
		
	}
	public String cleanString(String in){
		String out = "";
		String arr[] =in.split("/");
		out = arr[0];
		return out;
	}
	
	// For testing
	public static void main(String[] args) {
	
		String query = "Who is the winner of oakland county?";
		NLPAction actionObj = new NLPAction();
		POSTagger posTagObj = new POSTagger();
		String taggedQuery= null;
		StringBuffer sb = new StringBuffer(query);
		if(sb.charAt(sb.length()-1) =='?'){
			sb.deleteCharAt(sb.length()-1);
		}
		query = sb.toString();
			
		}
		try {
			actionObj.genQuery(query);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
