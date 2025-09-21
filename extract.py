from pathlib import Path
from grobid_client.grobid_client import GrobidClient

PDF_DIR = Path("D:/testpdf")          # 输入PDF目录
OUTPUT_DIR = Path("./output_xml")     # 输出XML目录
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


def batch_process():
    client = GrobidClient(config_path="./config.json")
    client.process(
        service="processFulltextDocument",
        input_path=str(PDF_DIR),
        output=OUTPUT_DIR,
        consolidate_header=True,
        n=1
    )
    # 可选：遍历输出目录，确认哪些文件已生成
    for xml_file in OUTPUT_DIR.glob("*.tei.xml"):
        print(f"Parsed: {xml_file.name}")



if __name__ == "__main__":
    batch_process()