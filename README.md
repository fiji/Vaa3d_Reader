[![](http://jenkins.imagej.net/job/Vaa3d_Reader/lastBuild/badge/icon)](http://jenkins.imagej.net/job/Vaa3d_Reader/)

Vaa3d_Reader
============

The Vaa3d_Reader plugin for Fiji permits the loading of two 3D image file formats in Fiji: v3draw and v3dpbd. 
These file formats were created for the neuroanatomy volume rendering program Vaa3D (http://www.vaa3d.org/).
These image formats are also used in other ways at HHMI Janelia Research Campus.

Both formats contain an initial 43 byte header, followed by binary image data. The v3dpbd data section
is compressed, while that of v3draw is uncompressed.

The V3dRawImageStream and allied classes could also be used to load such images in other Java programs.
