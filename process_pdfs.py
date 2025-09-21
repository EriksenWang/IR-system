import os
import requests
import json
from lxml import etree
from tqdm import tqdm
import time

# --- 配置 (保持不变) ---
PDF_INPUT_DIR = 'pdf_test'
JSON_OUTPUT_DIR = 'output_test'
GROBID_API_URL = 'http://localhost:8070/api/processFulltextDocument'


# --- 辅助函数：解析Grobid返回的XML (已升级) ---
def parse_grobid_xml(xml_content):
    """
    使用 lxml 解析 Grobid 返回的 XML 数据。
    这个版本更健壮，能处理更多样的XML结构。
    """
    if isinstance(xml_content, str):
        xml_content = xml_content.encode('utf-8')

    ns = {'tei': 'http://www.tei-c.org/ns/1.0'}

    try:
        root = etree.fromstring(xml_content)
    except etree.XMLSyntaxError as e:
        print(f"XML 解析错误: {e}")
        return None

    # --- 提取标题 (保持不变) ---
    title_element = root.find('.//tei:titleStmt/tei:title', ns)
    title = title_element.text.strip() if title_element is not None and title_element.text else "N/A"

    # --- 提取作者 (*** 升级版逻辑 ***) ---
    authors = []
    # 查找所有包含作者信息的 <author> 标签
    author_elements = root.findall('.//tei:analytic//tei:author', ns)
    for author_element in author_elements:
        # 在每个<author>标签内找到<persName>，这里包含了完整的姓名结构
        pers_name = author_element.find('.//tei:persName', ns)
        if pers_name is not None:
            # 提取所有 <forename> (名和中间名)
            forenames = pers_name.findall('.//tei:forename', ns)
            firstname_parts = [fn.text.strip() for fn in forenames if fn.text]

            # 提取 <surname> (姓)
            surname_element = pers_name.find('.//tei:surname', ns)
            lastname = surname_element.text.strip() if surname_element is not None and surname_element.text else ""

            # 拼接成完整的名字
            full_name = " ".join(firstname_parts + [lastname])

            authors.append({
                'full_name': full_name,
                'firstname': " ".join(firstname_parts),  # 名+中间名
                'lastname': lastname
            })

    # --- 提取摘要 (*** 升级版逻辑 ***) ---
    abstract = "N/A"
    # 先找到 <abstract> 标签
    abstract_element = root.find('.//tei:abstract', ns)
    if abstract_element is not None:
        # 使用 .itertext() 获取 <abstract> 标签下所有子孙节点的文本内容，并拼接起来
        # 这可以处理摘要中包含多个 <p> 或其他标签的情况
        abstract_text_parts = [text.strip() for text in abstract_element.itertext()]
        # 过滤掉空字符串并用换行符连接
        full_abstract = "\n".join(filter(None, abstract_text_parts))
        if full_abstract:
            abstract = full_abstract

    # --- 提取正文 (保持不变) ---
    body_text_parts = []
    # 查找正文部分的所有 <p> 标签
    body_text_elements = root.findall('.//tei:body//tei:p', ns)
    for p in body_text_elements:
        # 同样使用 itertext() 来获取段落内所有文本，防止有 <sup>, <sub> 等标签
        p_text = "".join(p.itertext()).strip()
        if p_text:
            body_text_parts.append(p_text)
    full_text = "\n\n".join(body_text_parts)  # 用双换行符分隔段落，更清晰

    # 组合成一个字典
    paper_data = {
        'title': title,
        'authors': authors,
        'abstract': abstract,
        'full_text': full_text
    }

    return paper_data


# --- 主逻辑 (保持不变) ---
def main():
    if not os.path.exists(JSON_OUTPUT_DIR):
        os.makedirs(JSON_OUTPUT_DIR)

    pdf_files = [f for f in os.listdir(PDF_INPUT_DIR) if f.endswith('.pdf')]
    print(f"找到 {len(pdf_files)} 个PDF文件，开始处理...")

    for pdf_filename in tqdm(pdf_files, desc="处理PDF"):
        pdf_path = os.path.join(PDF_INPUT_DIR, pdf_filename)
        json_filename = os.path.splitext(pdf_filename)[0] + '.json'
        json_path = os.path.join(JSON_OUTPUT_DIR, json_filename)

        if os.path.exists(json_path):
            continue

        try:
            with open(pdf_path, 'rb') as f:
                files = {'input': (pdf_filename, f, 'application/pdf')}
                response = requests.post(GROBID_API_URL, files=files, timeout=60)  # 超时延长到60秒

            if response.status_code == 200:
                paper_data = parse_grobid_xml(response.content)
                if paper_data:
                    with open(json_path, 'w', encoding='utf-8') as jf:
                        json.dump(paper_data, jf, ensure_ascii=False, indent=4)
            else:
                tqdm.write(f"处理失败: {pdf_filename}, 状态码: {response.status_code}, 原因: {response.text}")

        except requests.exceptions.RequestException as e:
            tqdm.write(f"请求异常: {pdf_filename}, 错误: {e}")
        except Exception as e:
            tqdm.write(f"发生未知错误: {pdf_filename}, 错误: {e}")

        time.sleep(0.1)

    print("所有PDF处理完成！")


if __name__ == '__main__':
    main()