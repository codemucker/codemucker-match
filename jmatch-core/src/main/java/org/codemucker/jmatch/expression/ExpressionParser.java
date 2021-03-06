package org.codemucker.jmatch.expression;

import org.codemucker.lang.IBuilder;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * Parse a logical expression of the form
 * 
 *  jim && !sam && ( bob || !alice || ( mary && tim ) ) && sue
 *  
 *  The non logical and grouping expressions are converted into matchers using the provided {@link ParseCallback}
 *  
 * @param <T>
 */
public class ExpressionParser<T> {

	private final String expression;
	private final int len;

	private int pos;
	private int startExp;
	private int groupCount;

	private final ParseCallback<T> callback;

	private final Token TOK_NOT = new Token("!", false);
	private final Token TOK_NOT_LONG = new Token("NOT", true);
	private final Token TOK_OR = new Token("||", false);
	private final Token TOK_OR_LONG = new Token("OR", true);
	private final Token TOK_AND = new Token("&&", false);
	private final Token TOK_AND_LONG = new Token("AND",true);
	
	private final Token TOK_START;// = new Token("(", false);
	private final Token TOK_END;//= new Token(")", false);

	private ExpressionParser(String expression,ParseCallback<T> callback, char braceStart, char braceEnd) {
		Preconditions.checkNotNull(callback,"expect parse callback");
		
		this.expression = expression;
		this.len = expression==null?0:expression.length();
		this.callback = callback;
		TOK_START = new Token("" + braceStart, false);
		TOK_END= new Token("" + braceEnd, false);
	}
	
	public static <T> T parse(String expression,ParseCallback<T> callback) throws ParseException {
		return parse(expression, callback,'(',')');
	}
	
	public static <T> T parse(String expression,ParseCallback<T> callback,char braceStart, char braceEnd) throws ParseException {
		ExpressionParser<T> parser = new ExpressionParser<T>(expression,callback,braceStart,braceEnd);
		try {
			return parser.parse();
		} catch (Exception e){
			String upto = parser.expression.substring(0, parser.pos);
			throw new ParseException("Error parsing expression '" + expression + "', error at position " + parser.pos + ", parsed up to '" + upto + "'",e); 
		}
	}
	
	private T parse(){
		callback.onStart();
		while (canRead()) {
			char c = read();
			if( c == '\''){//start of escape
				//read until end of escape
				startNextExpression();
				if(readNextUntil('\'')){
					endLastExpression();
					startNextExpression();
				} else {
					throw new ParseException("No closing single quote found");
				}
			} else if (isConsumedToken(TOK_NOT) || isConsumedToken(TOK_NOT_LONG)) {
				callback.onNegate();
			} else if (isConsumedToken(TOK_AND) || isConsumedToken(TOK_AND_LONG)) {
				callback.onAND();
			} else if (isConsumedToken(TOK_OR) || isConsumedToken(TOK_OR_LONG)) {
				callback.onOR();
			} else if(isConsumedToken(TOK_START)){
				groupCount++;
				callback.onStartGroup();
			} else if (isConsumedToken(TOK_END)) {
				if (groupCount <= 0) {
					throw new ParseException("Invalid expression, expect a '" + TOK_START.value + "' before '" + TOK_END.value + "' at character " + pos);
				}
				groupCount--;
				callback.onEndGroup();
			}
			nextChar();
		}
		endLastExpression();
		callback.onEnd();
		
		return callback.build();
	}

	private boolean isConsumedToken(Token token){
		if(isToken(token)){
			endLastExpression();
			consumeToken(token);
			startNextExpression();
			return true;
		}
		return false;
	}
	
	private boolean isToken(Token token){
		if(read() == token.firstChar && expression.startsWith(token.value, pos)){
			if(token.requiresWhitespace){
				int posAfter = pos + token.value.length();
				int posBefore= pos - 1;
				return ( (posBefore < 0 || Character.isWhitespace(expression.charAt(posBefore))) && (posAfter >= expression.length() || Character.isWhitespace(expression.charAt(posAfter))));
			}
			return true;
		}
		return false;
	}
	
	private void consumeToken(Token token){
		int next = pos + token.value.length() -1;
		if(token.requiresWhitespace){
			next++;//point to the whitespace
		}
		if(next > len){
			next = len;
		}
		pos = next;
	
	}
	
	private static class Token {
		final String value;
		final char firstChar;
		
		final boolean requiresWhitespace;
		
		public Token(String value, boolean whitespaceTerminates) {
			super();
			this.value = value;
			this.firstChar = value.charAt(0);
			this.requiresWhitespace = whitespaceTerminates;
		}
	}
	
	private boolean readNextUntil(char until) {
		for (int i = pos + 1; i < len; i++) {
			if (expression.charAt(i) == until) {
				pos = i;
				return true;
			}
		}
		return false;
	}

	private void startNextExpression() {
		startExp = pos + 1;
	}

	private void endLastExpression() {
		if (startExp < pos) {
			String exp = Strings.emptyToNull(expression.substring(startExp, pos).trim());
			if (exp != null) {
				callback.onExpression(exp);
			}
		}
	}

	private boolean canRead() {
		return pos < len;
	}

	private boolean hasNext() {
		return pos < len - 1;
	}

	private char read() {
		return expression.charAt(pos);
	}

	private boolean nextChar() {
		pos++;
		if (pos > len) {
			pos = len;
		}
		if (hasNext()) {
			return true;
		}
		return false;
	}

	public static interface ParseCallback<T> extends IBuilder<T> {

		void onStart();

		void onEndGroup();

		void onStartGroup();

		void onAND();

		void onOR();

		void onNegate();

		void onExpression(String expression);

		void onEnd();
		
	}

}