package org.ofdrw.graphics2d;

import org.ofdrw.core.basicStructure.doc.CT_CommonData;
import org.ofdrw.core.basicStructure.doc.CT_PageArea;
import org.ofdrw.core.basicStructure.doc.Document;
import org.ofdrw.core.basicStructure.ofd.DocBody;
import org.ofdrw.core.basicStructure.ofd.OFD;
import org.ofdrw.core.basicStructure.ofd.docInfo.CT_DocInfo;

import org.ofdrw.core.basicStructure.pageTree.Page;
import org.ofdrw.core.basicStructure.pageTree.Pages;
import org.ofdrw.core.basicStructure.res.CT_MultiMedia;
import org.ofdrw.core.basicStructure.res.MediaType;
import org.ofdrw.core.basicStructure.res.Res;
import org.ofdrw.core.basicStructure.res.resources.DrawParams;
import org.ofdrw.core.basicStructure.res.resources.MultiMedias;
import org.ofdrw.core.basicType.ST_ID;
import org.ofdrw.core.basicType.ST_Loc;

import org.ofdrw.core.pageDescription.drawParam.CT_DrawParam;
import org.ofdrw.gv.GlobalVar;
import org.ofdrw.pkg.container.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 图形OFD文档对象
 *
 * @author 权观宇
 * @since 2023-1-18 09:45:18
 */
public class OFDGraphicsDocument implements Closeable {
    /**
     * 打包后OFD文档存放路径
     */
    private Path outPath;

    /**
     * 文档是否已经关闭
     * true 表示已经关闭，false 表示未关闭
     */
    private boolean closed = false;

    /**
     * OFD公共资源
     * <p>
     * 所有图形图像都将存储于公共资源中
     */
    public final Res publicRes;

    /**
     * 多媒体清单，用于记录添加到文档的资源信息
     * <p>
     * 请不要直接使该参数，应通过 {@link OFDGraphicsDocument#obtainMedias()}
     */
    private MultiMedias medias;

    /**
     * 绘制参数清单
     * <p>
     * 请不要直接使该参数，应通过 {@link OFDGraphicsDocument#obtainDrawParam()}
     */
    private DrawParams drawParams;

    /**
     * OFD 打包
     */
    public final OFDDir ofdDir;

    /**
     * 当前文档中所有对象使用标识的最大值。
     * 初始值为 0。MaxUnitID主要用于文档编辑，
     * 在向文档增加一个新对象时，需要分配一个
     * 新的标识符，新标识符取值宜为 MaxUnitID + 1，
     * 同时需要修改此 MaxUnitID值。
     */
    public final AtomicInteger MaxUnitID = new AtomicInteger(0);


    /**
     * 文档属性信息，该对象会在初始化是被创建并且添加到文档中
     * 此处只是保留引用，为了方便操作。
     */
    public CT_CommonData cdata;


    /**
     * OFD文档对象
     */
    public final Document document;

    /**
     * 正在操作的文档目录
     */
    public final DocDir docDir;


    /**
     * 在指定路径位置上创建一个OFD文件
     *
     * @param outPath OFD输出路径
     */
    public OFDGraphicsDocument(Path outPath) {
        this();
        if (outPath == null) {
            throw new IllegalArgumentException("OFD文件存储路径(outPath)为空");
        }
        if (Files.isDirectory(outPath)) {
            throw new IllegalArgumentException("OFD文件存储路径(outPath)不能是目录");
        }
        if (!Files.exists(outPath.getParent())) {
            throw new IllegalArgumentException("OFD文件存储路径(outPath)上级目录 [" + outPath.getParent().toAbsolutePath() + "] 不存在");
        }
        this.outPath = outPath;
    }

    /**
     * 文档初始化构造器
     */
    private OFDGraphicsDocument() {
        // 初始化文档对象
        CT_DocInfo docInfo = new CT_DocInfo()
                .setDocID(UUID.randomUUID())
                .setCreationDate(LocalDate.now())
                .setCreator("OFD R&W")
                .setCreatorVersion(GlobalVar.Version);
        DocBody docBody = new DocBody()
                .setDocInfo(docInfo)
                .setDocRoot(new ST_Loc("Doc_0/Document.xml"));
        OFD ofd = new OFD().addDocBody(docBody);

        // 创建一个低层次的文档对象
        document = new Document();
        cdata = new CT_CommonData();
        // 默认页面大小为A4
        CT_PageArea defaultPageSize = new CT_PageArea()
                .setPhysicalBox(0, 0, 210d, 297d)
                .setApplicationBox(0, 0, 210d, 297d);
        // 默认使用RGB颜色空间所以此处不设置颜色空间
        // 设置页面属性
        cdata.setPageArea(defaultPageSize);
        document.setCommonData(cdata)
                // 空的页面引用集合，该集合将会在解析虚拟页面时得到填充
                .setPages(new Pages());

        ofdDir = OFDDir.newOFD()
                .setOfd(ofd);
        // 创建一个新的文档
        DocDir docDir = ofdDir.newDoc();
        this.docDir = docDir;
        docDir.setDocument(document);

        // 创建公共资源清单，容器目录为文档根目录下的Res目录
        publicRes = new Res().setBaseLoc(ST_Loc.getInstance("Res"));
        docDir.setPublicRes(publicRes);
        cdata.addPublicRes(ST_Loc.getInstance("PublicRes.xml"));
    }

    /**
     * 获取媒体清单，如果存在则创建
     *
     * @return 媒体清单
     */
    private MultiMedias obtainMedias() {
        if (this.medias == null) {
            this.medias = new MultiMedias();
            publicRes.addResource(this.medias);
        }
        return this.medias;
    }

    /**
     * 获取绘制参数清单，如果存在则创建
     *
     * @return 绘制参数清单
     */
    private DrawParams obtainDrawParam() {
        if (this.drawParams == null) {
            this.drawParams = new DrawParams();
            publicRes.addResource(this.drawParams);
        }
        return this.drawParams;
    }

    /**
     * 创建页面，单位毫米
     *
     * @param width  页面宽度，单位：毫米
     * @param height 页面高度，单位：毫米
     * @return 2D图形绘制对象
     */
    public OFDPageGraphics2D newPage(double width, double height) {
        CT_PageArea size = new CT_PageArea()
                .setPhysicalBox(0, 0, width, height)
                .setApplicationBox(0, 0, width, height);
        return newPage(size);
    }

    /**
     * 创建新页面，返回该页面2D图形对象
     *
     * @param pageSize 页面大小配置
     * @return 2D图形绘制对象
     */
    public OFDPageGraphics2D newPage(CT_PageArea pageSize) {
        final Pages pages = document.getPages();
        // 如果存在Pages那么获取，不存在那么创建
        final PagesDir pagesDir = docDir.obtainPages();

        // 创建页面容器
        PageDir pageDir = pagesDir.newPageDir();
        String pageLoc = String.format("Pages/Page_%d/Content.xml", pageDir.getIndex());
        final Page page = new Page(MaxUnitID.incrementAndGet(), pageLoc);
        pages.addPage(page);

        // 创建页面对象
        org.ofdrw.core.basicStructure.pageObj.Page pageObj = new org.ofdrw.core.basicStructure.pageObj.Page();
        if (pageSize != null) {
            pageObj.setArea(pageSize);
        }else{
            pageSize = this.cdata.getPageArea();
        }
        pageDir.setContent(pageObj);

        return new OFDPageGraphics2D(this, pageDir, pageObj,pageSize.getBox() );
    }

    /**
     * 添加图片资源
     *
     * @param img 图片渲染对象
     * @return 资源ID
     * @throws RuntimeException 图片转写IO异常
     */
    public ST_ID addResImg(Image img) {
        if (img == null) {
            return null;
        }
        final ResDir resDir = docDir.obtainRes();
        final Path resDirPath = resDir.getContainerPath();
        final File imgFile;
        try {
            imgFile = File.createTempFile("res", ".png", resDirPath.toFile());
            BufferedImage bi;
            if (img instanceof BufferedImage) {
                bi = (BufferedImage) img;
            }else{
                bi   = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                Graphics g2 = bi.getGraphics();
                g2.drawImage(img, 0, 0, null);
                g2.dispose();
            }
            ImageIO.write(bi, "png", imgFile);
        } catch (IOException e) {
            throw new RuntimeException("graphics2d 图片写入IO异常",e);
        }
        // 生成加入资源的ID
        ST_ID id = new ST_ID(MaxUnitID.incrementAndGet());
        // 将文件加入资源容器中
        // 创建图片对象，为了保持透明图片的兼容性采用PNG格式
        CT_MultiMedia multiMedia = new CT_MultiMedia()
                .setType(MediaType.Image)
                .setFormat("PNG")
                .setMediaFile(resDir.getAbsLoc().cat(imgFile.getName()))
                .setID(id);
        // 加入媒体类型清单
        obtainMedias().addMultiMedia(multiMedia);
        return id;
    }

    /**
     * 添加绘制参数至资源文件中
     *
     * @param drawParam 绘制参数
     * @return 资源对象ID
     */
    public ST_ID addDrawParam(CT_DrawParam drawParam) {
        if (drawParam == null) {
            return null;
        }
        ST_ID id = new ST_ID(MaxUnitID.incrementAndGet());
        drawParam.setObjID(id);
        obtainDrawParam().addDrawParam(drawParam);
        return id;
    }

    /**
     * 生成新的文档内对象ID
     *
     * @return 文档内对象ID
     */
    public ST_ID newID() {
        return new ST_ID(MaxUnitID.incrementAndGet());
    }


    @Override
    public void close() throws IOException {
        if (this.closed) {
            return;
        } else {
            closed = true;
        }

        try {
            // 设置最大对象ID
            cdata.setMaxUnitID(MaxUnitID.get());
            // final. 执行打包程序
            if (outPath != null) {
                ofdDir.jar(outPath.toAbsolutePath());
            } else {
                throw new IllegalArgumentException("OFD文档输出地址错误或没有设置输出流");
            }
        } finally {
            if (ofdDir != null) {
                // 清除在生成OFD过程中的工作区产生的文件
                ofdDir.clean();
            }
        }
    }
}
