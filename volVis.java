import java.io.DataInputStream;
import java.io.FileInputStream;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.lang.Math;
import java.awt.Color;

class Volume
{
	int data[][][];
	float zoom=4.f;
	int resolution=1024;
	float samplingDistance=0.25f;

	int accelerationStructureBlockSize=8;
	public int accelerationStructure[][][]=null; // A structure grouping the maximimum of 8x8x8 voxel blocks 

	/**
	* This function reads a volume dataset from disk and put the result in the data array
	* @param amplification allows increasing the brightness of the slice by a constant.
	*/
	public int GetResolution() 
	{
		return resolution;
	}
	
	public void SetResolution(int res) 
	{
		resolution=res;
	}
	
	public float GetZoom() 
	{
		return zoom;
	}
	
	public void SetZoom(float z) 
	{
		zoom=z;
	}
	
	boolean ReadData(String fileName, int sizeX, int sizeY, int sizeZ, int headerSize)
	{
		int cpt=0;
		byte dataBytes[]=new byte [sizeX*sizeY*sizeZ+headerSize];
		data = new int[sizeZ][sizeY][sizeX];
	    try
		{
			FileInputStream f = new FileInputStream(fileName);
			DataInputStream d = new DataInputStream(f);

			d.readFully(dataBytes);
			
			//Copying the byte values into the floating-point array

			for (int k=0;k<sizeZ;k++)
				for (int j=0;j<sizeY;j++)
					for (int i=0;i<sizeX;i++)
						data[k][j][i]=dataBytes[k*256*sizeY+j*sizeX+i+headerSize] & 0xff;
		}
		catch(Exception e)
		{ 
			System.out.println("Exception : "+cpt+e);
			return false;
		}
		return true;
	}
	
	public void CreateAccelerationStructure()
	{
		int dimX=data[0][0].length;
		int dimY=data[0].length;
		int dimZ=data.length;
		int newDimX=(dimX/accelerationStructureBlockSize)+1;
		int newDimY=(dimY/accelerationStructureBlockSize)+1;
		int newDimZ=(dimZ/accelerationStructureBlockSize)+1;
		accelerationStructure=new int[dimY][dimX][newDimZ]; //We put the z axis at the end to improve memory accesses
		for (int j = 0; j < newDimY; j++) 
			for (int i = 0; i < newDimX; i++)		
				for (int k = 0; k < newDimZ; k++) 
				{
					accelerationStructure[j][i][k]=0;
					for (int j2 = 0; j2 < newDimY+1; j2++) //+1 is needed to compute max as 9x9x9 samples are needed for a block of 8x8x8 cells due to interpolation
						for (int i2 = 0; i2 < newDimX+1; i2++)		
							for (int k2 = 0; k2 < newDimZ+1; k2++) 
							{
								int x=i*accelerationStructureBlockSize+i2;
								int y=j*accelerationStructureBlockSize+j2;
								int z=k*accelerationStructureBlockSize+k2;
								if (x<dimX && y<dimY && z<dimZ && accelerationStructure[j][i][k]<data[z][y][x]) // looking for max if coordinate is inside volume
									accelerationStructure[j][i][k]=data[z][y][x];
							}
				}
	}
	
	/**
	* This function returns the 3D gradient for the volumetric dataset (data variable). Note that the gradient values at the sides of the volume is not be computable. Each cell element containing a 3D vector, the result is therefore a 4D array.
	*/
	int [][][][] Gradient()
	{
		int[][][][] gradient=null;
		int dimX=data[0][0].length;
		int dimY=data[0].length;
		int dimZ=data.length;
		gradient=new int[dimZ-2][dimY-2][dimX-2][3]; //-2 due gradient not being computable at borders 
		for (int k = 1; k < dimZ-1; k++) 
			for (int j = 1; j < dimY-1; j++) 
				for (int i = 1; i < dimX-1; i++)
				{
						gradient[k-1][j-1][i-1][0]=(data[k][j][i+1]-data[k][j][i-1])/2;
						gradient[k-1][j-1][i-1][1]=(data[k][j+1][i]-data[k][j-1][i])/2;
						gradient[k-1][j-1][i-1][2]=(data[k+1][j][i]-data[k-1][j][i])/2;
				}
		return gradient;
	}


	double TrilinearInterpolation(double x, double y, double z)
	{
		int dimX=data[0][0].length;
		int dimY=data[0].length;
		int dimZ=data.length;
		
		if  (x<=0 || y<=0 || z<=0 || x>=dimX-1 || y>=dimY-1 || z>=dimZ-1 )
			return 0; //In case sample is out of volume
		int xint = (int) Math.floor(x);
		int yint = (int) Math.floor(y);
		int zint = (int) Math.floor(z);
		x=x-xint;
		y=y-yint;
		z=z-zint;
		return
			data[zint][yint][xint]*(1-x)*(1-y)*(1-z)+
			data[zint][yint][xint+1]*(x)*(1-y)*(1-z)+
			data[zint][yint+1][xint]*(1-x)*(y)*(1-z)+
			data[zint][yint+1][xint+1]*(x)*(y)*(1-z)+
			data[zint+1][yint][xint]*(1-x)*(1-y)*(z)+
			data[zint+1][yint][xint+1]*(x)*(1-y)*(z)+
			data[zint+1][yint+1][xint]*(1-x)*(y)*(z)+
			data[zint+1][yint+1][xint+1]*(x)*(y)*(z);
	}
	double[] TrilinearInterpolationGradient(int [][][][] gradient, double x, double y, double z)
	{
		int dimX=data[0][0].length;
		int dimY=data[0].length;
		int dimZ=data.length;
		double g[]=new double[3];
		
		 x=x-1;//-1 needed due to alignment between data and gradient as gradient is not computed at the border
		 y=y-1;
		 z=z-1;
		if  (x<=0 || y<=0 || z<=0 || x>=dimX-3 || y>=dimY-3 || z>=dimZ-3 )
			return null; //In case sample is out of gradient area
		int xint = (int) Math.floor(x);  
		int yint = (int) Math.floor(y);
		int zint = (int) Math.floor(z);
		x=x-xint;
		y=y-yint;
		z=z-zint;
		for (int i=0;i<3;i++)
		{
			g[i]=
				gradient[zint][yint][xint][i]*(1-x)*(1-y)*(1-z)+
				gradient[zint][yint][xint+1][i]*(x)*(1-y)*(1-z)+
				gradient[zint][yint+1][xint][i]*(1-x)*(y)*(1-z)+
				gradient[zint][yint+1][xint+1][i]*(x)*(y)*(1-z)+
				gradient[zint+1][yint][xint][i]*(1-x)*(1-y)*(z)+
				gradient[zint+1][yint][xint+1][i]*(x)*(1-y)*(z)+
				gradient[zint+1][yint+1][xint][i]*(1-x)*(y)*(z)+
				gradient[zint+1][yint+1][xint+1][i]*(x)*(y)*(z);
		}
		return g;
	}



	/**
	* This function returns an image of an isosurface visualisation projected along the z axis.
	* @param direction The direction of the ray along the axis
	* @param isovalue The threshold value for delimitating the isosurface
	*/

	public int[][] RenderIso(int [][][][] gradient, int isovalue, boolean positiveDirection)
	{
		int image[][]=new int[resolution][resolution];
		for (int j = 0; j < resolution; j++)
			for (int i = 0; i < resolution; i++)
			{
				image[j][i]=0;
				double k=0;
				while (k<data.length-1) //We keep going along the z direction for this ray-casting algorithm. Note the access to memory is less efficient this way than working by slices.
				{
					if (
						accelerationStructure[((int) (j/zoom))/accelerationStructureBlockSize][((int) (i/zoom))/accelerationStructureBlockSize][(int) k/accelerationStructureBlockSize]>=isovalue
					)
					{
						double inter=TrilinearInterpolation(i/zoom,j/zoom,k);
						if (inter>isovalue)
						{	
							double g[]=TrilinearInterpolationGradient(gradient,i/zoom,j/zoom,k);
							if (g!=null)
							{
								//Normalise gradient before shading
								double norm=Math.sqrt(g[0]*g[0]+g[1]*g[1]+g[2]*g[2]);
								image[j][i]=Math.min((int) Math.abs(255.*g[2]/norm),255);
							}
						}
						k+=samplingDistance;
					}
					else
						k+=accelerationStructureBlockSize;
				}
			}
		return image;
	}

	/**
	* This function returns an image of a contour visualisation projected along the z axis. Only for 3rd year students to complete
	* @param direction The direction of the ray along the axis
	* @param isovalue The threshold value for delimitating the isosurface
	*/

	public int[][] RenderContour(int [][][][] gradient, int isovalue, boolean positiveDirection)
	{
		int image[][]=new int[resolution][resolution];
		for (int j = 0; j < resolution; j++)
			for (int i = 0; i < resolution; i++)
			{
				double sum=0;
				double k=0;
				while (k<data.length-1) //We keep going along the z direction for this ray-casting algorithm. Note the access to memory is less efficient this way than working by slices.
				{
					double inter=TrilinearInterpolation(i/zoom,j/zoom,k);
					if (inter>isovalue)
					{	
						double g[]=TrilinearInterpolationGradient(gradient,i/zoom,j/zoom,k);
						if (g!=null)
						{
							double norm=g[0]*g[0]+g[1]*g[1]+g[2]*g[2];
							sum+=norm;
						}
					}
					k+=samplingDistance;
				}
				image[j][i]=(int) (255.*sum/(20000.+sum)); //Scaling data ina range 0 255
			}
		return image;
	}

	/**
	* This function swaps the x or y dimension with the z one, allowing projection on other faces of the volume.
	*/	
	void SwapZAxis(int axis)
	{
		if (axis==2)
			return;
		int dimX=data[0][0].length;
		int dimY=data[0].length;
		int dimZ=data.length;
		int newvol[][][];
		if (axis==0)
		{
			newvol=new int[dimX][dimY][dimZ];
			for (int k = 0; k < dimZ; k++) 
				for (int j = 0; j < dimY; j++) 
					for (int i = 0; i < dimX; i++)
						newvol[i][j][k]=data[k][j][i];
		}
		else
		{
			newvol=new int[dimY][dimZ][dimX];
			for (int k = 0; k < dimZ; k++) 
				for (int j = 0; j < dimY; j++) 
					for (int i = 0; i < dimX; i++)
						newvol[j][k][i]=data[k][j][i];
		}
		data=newvol;
	}
}

public class volVis
{
	
	public static void SaveImage(String name, int[][] im)
	{
		BufferedImage image = new BufferedImage(im.length, im[0].length, BufferedImage.TYPE_BYTE_GRAY );
		for (int j = 0; j < im.length; j++) 
			for (int i = 0; i < im[0].length; i++) 
				image.setRGB(j, i, im[j][i]*256*256+im[j][i]*256+im[j][i]);
		
		File f = new File(name);
		try 
		{
			ImageIO.write(image, "tiff", f);
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
	}

	public static void SaveImageRGB(String name, int[][][] im)
	{
		BufferedImage image = new BufferedImage(im.length, im[0].length, BufferedImage.TYPE_INT_RGB );
		for (int j = 0; j < im.length; j++) 
			for (int i = 0; i < im[0].length; i++) 
			{
				Color c=new Color(Math.abs(im[j][i][0]),Math.abs(im[j][i][1]),Math.abs(im[j][i][2]));
				image.setRGB(j, i, c.getRGB());
			}
		
		File f = new File(name);
		try 
		{
			ImageIO.write(image, "tiff", f);
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
	}

	public static void main(String[] args) 
	{
		//Args: width height depth header_size isovalue projection_axis direction
		//A command line example: java vis 256 256 225 62 95 0 false
		Volume v=new Volume();
		v.ReadData("./bighead_den256X256X225B62H.raw",Integer.parseInt(args[0]),Integer.parseInt(args[1]),Integer.parseInt(args[2]),Integer.parseInt(args[3]));
		v.SwapZAxis(Integer.parseInt(args[5]));
		v.CreateAccelerationStructure();

		int[][][][] gradient =v.Gradient();
		System.out.println("Render ISO");
		int[][] im=v.RenderIso(gradient,Integer.parseInt(args[4]),Boolean.parseBoolean(args[6]));
		if (im!=null)
		{
			SaveImage("iso.tiff",im);
			SaveImage("iso"+SUID()+".tiff",im);
		}
		System.out.println("Render Contour");
		im=v.RenderContour(gradient,Integer.parseInt(args[4]),Boolean.parseBoolean(args[6]));
		if (im!=null)
		{
			SaveImage("contour.tiff",im);
			SaveImage("contour"+SUID()+".tiff",im);
		}
	}
}