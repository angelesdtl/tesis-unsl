/**
 * 
 */
package tesis.data

import com.sun.xml.internal.bind.v2.util.EditDistance

/**
 * @author lsperanza
 *
 */
class ItemSignature
{
	def dists//Array de distancias
	long itemPosition;
	

	public ItemSignature(String itemTitle, List pivotes)
	{
		dists = new int[pivotes.size()]
		
		for(int i=0; i< pivotes.size(); i++)
		{
			dists[i] = EditDistance.editDistance(itemTitle, pivotes[i].getItemTitle())
		}
	}


	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
		return "ItemSignature [dists=" + dists + ", itemPosition=" + itemPosition + "]";
	}
}