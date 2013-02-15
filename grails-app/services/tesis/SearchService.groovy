package tesis

import tesis.data.CategDto
import tesis.data.ItemSignature;
import tesis.file.manager.RandomAccessFileManager
import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.codehaus.groovy.grails.web.json.JSONObject
import tesis.utils.Utils
import com.sun.xml.internal.bind.v2.util.EditDistance;


class SearchService {

    static transactional = true

    def sequentialSearch(String itemTitle, String categ,int radio, IndexManager mgr)
	{
		int pos = mgr.categs.search(new CategDto(categName:params.categ,signatures:null))
		//Obtengo las firmas de los items para poder buscarlos en el archivo
		def signatures = mgr.categs.get(pos).signatures
		return getItemsFromFile(signatures, itemTitle, radio)
    }
	
	def simpleSearch(String itemTitle, String categ,int radio, IndexManager mgr)
	{
		def signatures = getCandidates(itemTitle,categ,radio,mgr)
		return getItemsFromFile(signatures, itemTitle, radio)
	}

	private getItemsFromFile(ArrayList<ItemSignature> signatures, String itemTitle, int radio) {
		ArrayList<JSONObject> itemsFound = new ArrayList<JSONObject>()

		RandomAccessFileManager rfm = new RandomAccessFileManager(ConfigurationHolder.config.itemsDataFileName)

		if (rfm.openFile("rw"))
		{
			signatures.each
			{
				def item =  new JSONObject(rfm.getItem(it.itemPosition,it.itemSize))
				def dist = EditDistance.editDistance(itemTitle, item.searchTitle)
				if(dist < radio)
				{
					itemsFound.add(item)
				}
			}
			rfm.closeFile()
		}
		return itemsFound
	}
	
	def getAllItemsByCateg(IndexManager mgr, String categ)
	{
		int pos = mgr.categs.search(new CategDto(categName:categ,signatures:null))
		
		def signatures = mgr.categs.get(pos).signatures
		
		ArrayList<JSONObject> itemsFound = new ArrayList<JSONObject>()
		RandomAccessFileManager rfm = new RandomAccessFileManager(ConfigurationHolder.config.itemsDataFileName)
		if (rfm.openFile("rw"))
		{
			signatures.each
			{
				def item =  new JSONObject(rfm.getItem(it.itemPosition,it.itemSize))
				itemsFound.add(item)
							
			}
			rfm.closeFile()
		}
		log.info "Total de items en la categ ${categ} : ${itemsFound.size()}"
		return itemsFound
	}
	
	def getCandidates(String itemTitle, String categ,int radio, IndexManager mgr){
		//Calculo la firma para la query
		ItemSignature sig = new ItemSignature(itemTitle, mgr.getPivotsForCateg(categ))
		int value
		ItemSignature candidato
		ArrayList<ItemSignature> candidatos = new ArrayList<ItemSignature>()

		//Obtengo todas las firmas para la categoria
		int pos = mgr.categs.search(new CategDto(categName:categ,signatures:null))
		
		def signatures = mgr.categs.get(pos).signatures

		//Comparo la firma de la query con las firmas de la categoria, si el valor es mayor que el radio, descarto el item
		signatures.each 
		{
			candidato = it
			for (int i = 0;i<it.dists.size();i++)
			{
				value = (sig.dists[i] - candidato.dists[i]).abs()
				if (value > radio)
				{
					i = candidato.dists.size()
					candidato=null
				}
			}
			if(candidato){
				candidatos.add(candidato)
			}
		}
		
		return candidatos
	}
}