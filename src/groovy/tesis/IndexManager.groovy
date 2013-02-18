/**
 * 
 */
package tesis

import grails.converters.JSON
import java.util.Map;
import java.util.List;
import tesis.data.CategDto;
import tesis.data.ElementsPairs
import tesis.data.ItemDto;
import tesis.data.ItemSignature;
import tesis.data.PivotDto
import tesis.file.manager.RandomAccessFileManager;
import tesis.file.manager.SimpleFileManager;
import tesis.structure.CategsHash;
import tesis.utils.Utils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.codehaus.groovy.grails.web.json.JSONArray
import org.codehaus.groovy.grails.web.json.JSONObject

import com.sun.xml.internal.bind.v2.util.EditDistance;
/**
 * @author lsperanza
 *
 */
class IndexManager
{
	Log log = LogFactory.getLog(IndexManager.class.getName())
	CategsHash categs = null;
	//Contiene la categoria y la lista de pivotes asociada.
	//Si se utiliza el mismo conjunto de pivotes p/todas
	//las categorias, se carga el par "ALL",[lista_pivotes]
	Map pivots = null;

	public IndexManager(String pivotStrategy, int cantPivots)
	{
		createCategsHash(createCategListFromFile());
		
		if("RANDOM".equals(pivotStrategy))
		{
			createPivots(cantPivots)
		}
		else if ("BY_CATEG_RND".equals(pivotStrategy))
		{
			//TODO: Implementar random por categoría
		}else{
			createPivotsIncr(cantPivots,ConfigurationHolder.config.elementsPairs,ConfigurationHolder.config.sizeSample)//TODO: la otra implementacion
		}
		
		createSignatures()
	}
	public IndexManager()
	{
		long startTime = System.currentTimeMillis()
		SimpleFileManager fm = new SimpleFileManager(ConfigurationHolder.config.pivotsFileName,"\n")
		pivots = [:]
		if(fm.openFile(0))
		{
			JSONObject pivotMap
			pivotMap = new JSONObject(fm.nextLine())
			fm.closeFile()
			pivotMap.each
			{
				key,value ->
				pivots.put(key.toString(), value.collect{new PivotDto(itemId:it.itemId,categ:it.categ,searchTitle:it.searchTitle)})
			}
			
		}
		else
		{
			throw new Exception("Error al abrir el archivo para escritura ${ConfigurationHolder.config.pivotsFileName}")
		}
		log.info("Archivo de pivots leido con exito")

		fm = new SimpleFileManager(ConfigurationHolder.config.categsFileName,"\n")
		
		if(fm.openFile(0))
		{
			ArrayList<CategDto> categsList = new ArrayList<CategDto>()
			String categLine
			while(categLine = fm.nextLine())
			{
				JSONObject jsonCateg = new JSONObject(categLine)
				
				JSONArray jsonSigs = new JSONArray(jsonCateg.signatures)
				
				ArrayList signatures = new ArrayList<ItemSignature>()
				for(JSONObject s in jsonSigs){
					signatures.add(new ItemSignature(s.dists, s.itemPosition, s.itemSize))
				}
					
				categsList.add(new CategDto(jsonCateg.categName,signatures))				
			}
			fm.closeFile()
			createCategsHash(categsList)
		}
		else
		{
			throw new Exception("Error al abrir el archivo para lectura ${ConfigurationHolder.config.categsFileName}")
		}
		
		
		log.info "Creacion de indice desde archivo: ${System.currentTimeMillis()-startTime} ms"
	}
	
	private createCategListFromFile()
	{
		long startTime = System.currentTimeMillis()
		SimpleFileManager fm = new SimpleFileManager(ConfigurationHolder.config.categsBaseFileName, ConfigurationHolder.config.textDataSeparator);
		ArrayList<CategDto> list=new ArrayList<CategDto>()
		
		if(fm.openFile(0))
		{
			CategDto dto;
			while((dto = fm.nextCateg()))
			{
				if(dto){list.add(dto)}
			}
			fm.closeFile();
		}
		else
		{
			throw new Exception("Error al abrir el archivo")
		}
		log.info "Lectura de categorias desde archivo: ${System.currentTimeMillis()-startTime} ms"
		return list
	}
	
	private void createCategsHash(ArrayList<CategDto> list)
	{
		long startTime = System.currentTimeMillis()
		categs = new CategsHash(list.size(), 0.4)
		for(CategDto c:list)
		{
			if(categs.add(c)==-1)
			{
				log.info "No se pudo agregar la categoria: ${c.getCategName()}"
			}
		}
		log.info "Cargado de categorias en el hash: ${System.currentTimeMillis()-startTime} ms"
	}
	private void createPivots(int cant)
	{
		pivots = [:]
		long startTime = System.currentTimeMillis()
		SimpleFileManager fm = new SimpleFileManager(ConfigurationHolder.config.itemsBaseFileName, ConfigurationHolder.config.textDataSeparator);
		String res;
		if(cant <= 50)
		{
			if(fm.openFile(0))
			{
				def pivs = []
				(1..cant).each
				{
					pivs.add(fm.nextPivot())
					Random rand = new Random()
					(1..rand.nextInt(5)).each
					{
						fm.nextPivot()
					}
				}
				fm.closeFile()
				pivots.put("ALL",pivs)
				log.info "${cant} pivotes cargados con exito"
			}
			else
			{
				throw new Exception("Error al abrir el archivo")
			}
		}
		else
		{
			throw new Exception("Cantidad de pivotes mayor a la permitida (50)")
		}
		log.info "Creacion de pivotes: ${System.currentTimeMillis()-startTime} ms"
	}
	private void createPivotsIncr(pivotsQty,aQty,nQty){
		pivots = [:]
		ArrayList<ElementsPairs> elemPairs
		long startTime = System.currentTimeMillis()
		SimpleFileManager fm = new SimpleFileManager(ConfigurationHolder.config.itemsBaseFileName, ConfigurationHolder.config.textDataSeparator);
		String res;
		def pCandidate
		def max, min	
		
		if(pivotsQty <= 50)
		{
			if(fm.openFile(0))
			{
				def pivs = []
				/** el primer pivot es elegido al azar*/
				pCandidate = getRandomPivot(fm)
				pivs.add(pCandidate)
				elemPairs = getElementsPairs(aQty,pivotsQty,pCandidate,fm)
				
				max = getMediaD(elemPairs,pivs, null)
				
				while(pivs.size() < pivotsQty){
					pCandidate = getRandomPivot(fm)
					while(pivs?.find{it.itemId == pCandidate.itemId}){						
						pCandidate = getRandomPivot(fm)
					}
					min = getMediaD(elemPairs,pivs,pCandidate)
					if(min>max){
						max=min
						for (e in elemPairs){
							e.addDistance(pCandidate)
						}
						pivs.add(pCandidate)
					}
				}				
				fm.closeFile()
				pivots.put("ALL",pivs)
				log.info "${pivotsQty} pivotes cargados con exito"
			}
			else
			{
				throw new Exception("Error al abrir el archivo")
			}
		}
		else
		{
			throw new Exception("Cantidad de pivotes mayor a la permitida (50)")
		}
		log.info "Creacion de pivotes: ${System.currentTimeMillis()-startTime} ms"
	}
	
	private void createSignatures()
	{
		long startTime = System.currentTimeMillis()
		def noCateg=0
		
		String res
		SimpleFileManager fm = new SimpleFileManager(ConfigurationHolder.config.itemsBaseFileName, ConfigurationHolder.config.textDataSeparator);
		RandomAccessFileManager rfm = new RandomAccessFileManager(ConfigurationHolder.config.itemsDataFileName)

		if(fm.openFile(0))
		{
			if(rfm.openFile("rw"))
			{
				rfm.resetFile();
				ItemDto curItem
				while(curItem = fm.nextItem())
				{
					ItemSignature sig = new ItemSignature(curItem.getSearchTitle(), getPivotsForCateg(curItem.getCateg()))
					CategDto catForSearch = new CategDto(categName:curItem.categ,signatures:null)
					int pos = categs.search(catForSearch)
					
					if (categs.get(pos).equals(catForSearch)){
						sig.setItemPosition(rfm.insertItem(curItem))
						sig.setItemSize(curItem.toJSON().toString().length())
						categs.get(pos).getSignatures().add(sig)
					}else{
						
						noCateg++
					}
				}
				rfm.closeFile()
				log.info "Items no almacenados por categoria invalida: " + noCateg
				
			}
			else
			{
				throw new Exception("Error al abrir el archivo para lectura/escritura")
			}
			fm.closeFile()
		}
		else
		{
			throw new Exception("Error al abrir el archivo ${ConfigurationHolder.config.itemsBaseFileName}")
		}
		
		log.info "Creacion de firmas: ${System.currentTimeMillis()-startTime} ms"
	}
	
	def getPivotsForCateg(String categName)
	{
		def ret = pivots.get(categName)
		if(!ret)
		{
			ret = pivots.get("ALL")
		}
		return ret
	}
	
	def createIndexFiles()
	{
		long startTime = System.currentTimeMillis()
		
		SimpleFileManager fm = new SimpleFileManager(ConfigurationHolder.config.categsFileName,"\n")
		
		if(fm.openFileW())
		{
			CategDto virgin = new CategDto(categName:ConfigurationHolder.config.VIRGIN_CELL,signatures:null);
			CategDto used = new CategDto(categName:ConfigurationHolder.config.USED_CELL,signatures:null);

			def listCategs = categs.getValues()

			for(int i=0;i < listCategs.size; i++)
			{
				if((!listCategs[i].equals(virgin))&&(!listCategs[i].equals(used)))
				{
					fm.insertObject(listCategs[i].toJSON())
				}
			}
			fm.closeFileW()
		}
		else
		{
			throw new Exception("Error al abrir el archivo para escritura ${ConfigurationHolder.config.categsFileName}")
		}
		fm = new SimpleFileManager(ConfigurationHolder.config.pivotsFileName,"\n")
		if(fm.openFileW()){
			Map pivs = [:]
			
			pivots.each 
			{
				key,value ->
				pivs.put(key, value.collect {it.toJSON()})
			}
			
			fm.insertObject(pivs as JSON)
			
			fm.closeFileW()
		}
		else
		{
			throw new Exception("Error al abrir el archivo para escritura ${ConfigurationHolder.config.pivotsFileName}")
		}
		log.info "Creacion de archivos de categs y pivots: ${System.currentTimeMillis()-startTime} ms"
	}
	
	def getRandomPivot(fm){
		Random rand = new Random()
		(1..rand.nextInt(5)).each
		{
			fm.nextPivot()
		}
		return fm.nextPivot()
	}
	
	def getElementsPairs(pairsQty,pivotsQty,pivot,file){
		ArrayList<ElementsPairs> pairs = new ArrayList<ElementsPairs>(pairsQty)
		ElementsPairs pair
		for (int i=0; i<pairsQty;i++){
			pair = new ElementsPairs()
			pair.a = getRandomPivot(file)
			pair.b = getRandomPivot(file)
			while (pair.a.itemId == pair.b.itemId){
				pair.b = getRandomPivot(file)
				log.info "mismo random en el par"
			}
			pair.initDist(pivotsQty,pivot)			
			pairs.add(pair)
		}
		return pairs
	}
	def getMediaD(pairs,pivots,pivotCandidate){
		def max
		def value
		def media = 0
		List a
		List b
		for (pair in pairs){
			a = pair.aDists.clone()
			b = pair.bDists.clone()
			if(pivotCandidate){
				a[Utils.firtsFree(pair.aDists)] = EditDistance.editDistance(pair.a.searchTitle, pivotCandidate.searchTitle)
				b[Utils.firtsFree(pair.aDists)] = EditDistance.editDistance(pair.b.searchTitle, pivotCandidate.searchTitle)
			}
			max = (a[0]-b[0]).abs()
			for(int i=1; i< a.size() && a[i]!=-1; i++){
				value = (a[i]-b[i]).abs()
				if(value>max){
					max=value
				}
			}
			media += max			
		}		
		return media/pairs.size()
	}
}


