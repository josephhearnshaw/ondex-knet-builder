package net.sourceforge.ondex.rdf.rdf2oxl.support;

import static java.lang.String.format;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * # The Concept Processor
 * 
 * This is a special {@link QueryProcessor}, which gets the concept URIs to be passed to the {@link ConceptHandler}
 * not from a SPARQL query, but auto-wired from {@link ConceptIdHandler}, which has to be invoked in advance in order
 * to get a URI/ID mapping.  
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>25 Jul 2018</dd></dl>
 *
 */
@Component ( "conceptProcessor" )
public class ConceptProcessor extends QueryProcessor
{	
	/**
	 * This is auto-wired from {@link ConceptIdHandler}.
	 */
	@Autowired @Qualifier ( "conceptIds" )
	private Map<String, Integer> conceptIds;
	
	/**
	 *  This comes from {@link GraphSummaryHandler} and is used to verify that the size of 
	 *  {@link #conceptIds fetched concept instances} matches the summary info in the RDF counts.
	 *  
	 *  @Autowired doesn't work very well for {@link Map}.
	 */
	@Resource ( name = "graphSummary" )
	private Map<String, Object> graphSummary;

	
	@Override
	public void process ( String foo, Object... opts )
	{		
		String logPrefix = this.getLogPrefix ();
		try
		{
			log.info ( "{}: Reusing previous IRIs", logPrefix );

			// Some sanity check
			long conceptsCount = (long) graphSummary.get ( "conceptsCount" );
			if ( conceptIds.size () != conceptsCount ) log.warn ( 
				"Mismatch between SPARQL-counted concepts ({}) and those collected ({})",
				conceptsCount, conceptIds.size ()
			);
						
			QuerySolutionHandler handler = (QuerySolutionHandler) getConsumer ();
			Writer outWriter = handler.getOutWriter ();
			
			if ( this.getHeader () != null ) outWriter.write ( this.getHeader () );
			
			@SuppressWarnings ( "unchecked" )
			List<QuerySolution> chunk[] = new List[] { this.getDestinationSupplier ().get () };

			Model model = ModelFactory.createDefaultModel ();
			
			this.conceptIds.forEach ( (iri, id) -> 
			{
				QuerySolutionMap sol = new QuerySolutionMap ();
				sol.add ( "resourceIri", model.createResource ( iri ) );
				chunk [ 0 ].add ( sol );
			
				// As usually, this triggers a new chunk-processing task when we have enough items to process.
				chunk [ 0 ] = handleNewTask ( chunk [ 0 ] );
			});
			
			// Process last chunk
			handleNewTask ( chunk [ 0 ], true );		
			this.waitExecutor ( this.getLogPrefix () + ": waiting for RDF resource processing tasks to finish" );
	
			if ( this.getTrailer () != null ) outWriter.write ( this.getTrailer () );
			
			log.info ( "{}: all RDF resources processed", logPrefix );
		}
		catch ( IOException ex )
		{
			throw new UncheckedIOException ( 
				format ( "%s: I/O error while processing RDF data: %s", logPrefix, ex.getMessage () ),
				ex
			);
		}
	}
	
}
